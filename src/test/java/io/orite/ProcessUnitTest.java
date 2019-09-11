package io.orite;


import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.extension.process_test_coverage.junit.rules.TestCoverageProcessEngineRuleBuilder;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;

/**
 * Test case starting an in-memory database-backed Process Engine.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class ProcessUnitTest {

  private static final String PROCESS_DEFINITION_KEY = "bpmn.tweet";
  public static final String USER_TASK_REVIEW_TWEET = "UserTask_ReviewTweet";
  public static final String END_EVENT_TWEET_REJECTED = "EndEvent_TweetRejected";
  public static final String END_EVENT_TWEET_PUBLISHED = "EndEvent_TweetPublished";

  static {
    LogFactory.useSlf4jLogging(); // MyBatis
  }

  //@Rule
  //public final ProcessEngineRule processEngine = new StandaloneInMemoryTestConfiguration().rule();

  @ClassRule
  @Rule
  public static ProcessEngineRule rule = TestCoverageProcessEngineRuleBuilder.create().build();

  /*@Rule
  public ProcessEngineRule rule = new ProcessEngineRule();

  @Before
  public void setup() {
    init(rule.getProcessEngine());
  }*/

  @Test
  @Deployment(resources = "process.bpmn")
  public void testHappyPath() {
    // Either: Drive the process by API and assert correct behavior by camunda-bpm-assert, e.g.:

    // Start process with Java API and variables
    //ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey(PROCESS_DEFINITION_KEY, variables);
    ProcessInstance processInstance = runtimeService()
            .startProcessInstanceByKey(PROCESS_DEFINITION_KEY,
                    Collections.singletonMap("content", Variables.stringValue("Chango peludo - "+new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date()))));

    org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat(processInstance).isWaitingAt(USER_TASK_REVIEW_TWEET).task().hasCandidateGroup("management");

    final List<Task> taskList = taskService()
            .createTaskQuery()
            .taskCandidateGroup("management")
            .processInstanceId(processInstance.getId())
            .list();
    assertThat(taskList).isNotNull().hasSize(1);
    Task task = taskList.get(0);

    taskService().complete(task.getId(), Collections.singletonMap("approved", true));

    List<Job> jobList = jobQuery()
            .processInstanceId(processInstance.getId())
            .list();
    assertThat(jobList).hasSize(1);
    execute(jobList.get(0));


    // Make assertions on the process instance
    org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat(processInstance)
            .isEnded()
            .hasPassed(END_EVENT_TWEET_PUBLISHED)
            .hasNotPassed(END_EVENT_TWEET_REJECTED);
    // Now: Drive the process by API and assert correct behavior by camunda-bpm-assert
  }


  @Test
  @Deployment(resources = "process.bpmn")
  public void testTweetRejected() {

    final ProcessInstance processInstance = runtimeService()
            .createProcessInstanceByKey(PROCESS_DEFINITION_KEY)
            .setVariables(new HashMap<String, Object>() {{
              put("content", Variables.stringValue("Chango peludo - "+new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date())));
              put("approved", false);
            }})
            .startAfterActivity(USER_TASK_REVIEW_TWEET)
            .execute();

    org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat(processInstance)
            .isWaitingAt("Task_NotifyRejection")
            .externalTask()
            .hasTopicName("notification");

    complete(externalTask());


    //org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat(processInstance).isEnded().hasPassed(END_EVENT_TWEET_REJECTED);
  }


  @Test
  @Deployment(resources = "process.bpmn")
  public void testTweetFromSuperUser() {
    ProcessInstance processInstance = runtimeService()
            .createMessageCorrelation("superUserTweet")
            .setVariable("content", Variables.stringValue("Chango peludo super user - "+new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date())))
            .correlateWithResult()
            .getProcessInstance();
    org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat(processInstance).isStarted();

    List<Job> jobList = jobQuery()
            .processInstanceId(processInstance.getId())
            .list();

    // execute the job
    assertThat(jobList).hasSize(1);
    execute(jobList.get(0));

    org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat(processInstance).isEnded();
  }

  @Test
  @Deployment(resources = "process.bpmn")
  public void testTweetWithdrawn() {
    ProcessInstance processInstance = runtimeService()
            .startProcessInstanceByKey(PROCESS_DEFINITION_KEY, Collections.singletonMap("content", "Test tweetWithdrawn message"));
    org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat(processInstance).isStarted().isWaitingAt(USER_TASK_REVIEW_TWEET);
    runtimeService()
            .createMessageCorrelation("tweetWithdrawn")
            .processInstanceVariableEquals("content", "Test tweetWithdrawn message")
            .correlateWithResult();
    org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat(processInstance).isEnded();
  }
}
