/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.engine.test.api.event;

import java.util.Collections;

import org.activiti.engine.delegate.event.ActivitiActivityEvent;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.delegate.event.ActivitiMessageEvent;
import org.activiti.engine.delegate.event.ActivitiSignalEvent;
import org.activiti.engine.impl.test.PluggableActivitiTestCase;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.test.Deployment;

/**
 * Test case for all {@link ActivitiEvent}s related to activities.
 * 
 * @author Frederik Heremans
 */
public class ActivityEventsTest extends PluggableActivitiTestCase {

	private TestActivitiActivityEventListener listener;

	/**
	 * Test events related to signalling
	 */
	@Deployment
	public void testActivitySignalEvents() throws Exception {
		// Two paths are active in the process, one receive-task and one
		// intermediate catching signal-event
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("signalProcess");
		assertNotNull(processInstance);

		// Check regular signal through API
		Execution executionWithSignal = runtimeService.createExecutionQuery().activityId("receivePayment").singleResult();
		assertNotNull(executionWithSignal);

		runtimeService.signal(executionWithSignal.getId());
		assertEquals(1, listener.getEventsReceived().size());
		assertTrue(listener.getEventsReceived().get(0) instanceof ActivitiSignalEvent);
		ActivitiSignalEvent signalEvent = (ActivitiSignalEvent) listener.getEventsReceived().get(0);
		assertEquals(ActivitiEventType.ACTIVITY_SIGNALED, signalEvent.getType());
		assertEquals("receivePayment", signalEvent.getActivityId());
		assertEquals(executionWithSignal.getId(), signalEvent.getExecutionId());
		assertEquals(executionWithSignal.getProcessInstanceId(), signalEvent.getProcessInstanceId());
		assertEquals(processInstance.getProcessDefinitionId(), signalEvent.getProcessDefinitionId());
		assertNull(signalEvent.getSignalName());
		assertNull(signalEvent.getSignalData());
		listener.clearEventsReceived();

		// Check signal using event, and pass in additional payload
		Execution executionWithSignalEvent = runtimeService.createExecutionQuery().activityId("shipOrder").singleResult();
		runtimeService.signalEventReceived("alert", executionWithSignalEvent.getId(),
		    Collections.singletonMap("test", (Object) "test"));
		assertEquals(1, listener.getEventsReceived().size());
		assertTrue(listener.getEventsReceived().get(0) instanceof ActivitiSignalEvent);
		signalEvent = (ActivitiSignalEvent) listener.getEventsReceived().get(0);
		assertEquals(ActivitiEventType.ACTIVITY_SIGNALED, signalEvent.getType());
		assertEquals("shipOrder", signalEvent.getActivityId());
		assertEquals(executionWithSignalEvent.getId(), signalEvent.getExecutionId());
		assertEquals(executionWithSignalEvent.getProcessInstanceId(), signalEvent.getProcessInstanceId());
		assertEquals(processInstance.getProcessDefinitionId(), signalEvent.getProcessDefinitionId());
		assertEquals("alert", signalEvent.getSignalName());
		assertNotNull(signalEvent.getSignalData());
		listener.clearEventsReceived();
	}

	/**
	 * Test to verify if signals coming from an intermediate throw-event trigger
	 * the right events to be dispatched.
	 */
	@Deployment
	public void testActivitySignalEventsWithinProcess() throws Exception {
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("signalProcess");
		assertNotNull(processInstance);

		Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
		assertNotNull(task);

		Execution executionWithSignalEvent = runtimeService.createExecutionQuery().activityId("shipOrder").singleResult();

		taskService.complete(task.getId());
		assertEquals(1L, listener.getEventsReceived().size());

		assertTrue(listener.getEventsReceived().get(0) instanceof ActivitiSignalEvent);
		ActivitiSignalEvent signalEvent = (ActivitiSignalEvent) listener.getEventsReceived().get(0);
		assertEquals(ActivitiEventType.ACTIVITY_SIGNALED, signalEvent.getType());
		assertEquals("shipOrder", signalEvent.getActivityId());
		assertEquals(executionWithSignalEvent.getId(), signalEvent.getExecutionId());
		assertEquals(executionWithSignalEvent.getProcessInstanceId(), signalEvent.getProcessInstanceId());
		assertEquals(processInstance.getProcessDefinitionId(), signalEvent.getProcessDefinitionId());
		assertEquals("alert", signalEvent.getSignalName());
		assertNull(signalEvent.getSignalData());
	}

	/**
	 * Test events related to message events, called from the API.
	 */
	@Deployment
	public void testActivityMessageEvents() throws Exception {
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("messageProcess");
		assertNotNull(processInstance);

		Execution executionWithMessage = runtimeService.createExecutionQuery().activityId("shipOrder").singleResult();
		assertNotNull(executionWithMessage);

		runtimeService.messageEventReceived("messageName", executionWithMessage.getId());
		assertEquals(2, listener.getEventsReceived().size());

		// First, a message-event is expected
		assertTrue(listener.getEventsReceived().get(0) instanceof ActivitiMessageEvent);
		ActivitiMessageEvent messageEvent = (ActivitiMessageEvent) listener.getEventsReceived().get(0);
		assertEquals(ActivitiEventType.ACTIVITY_MESSAGE_RECEIVED, messageEvent.getType());
		assertEquals("shipOrder", messageEvent.getActivityId());
		assertEquals(executionWithMessage.getId(), messageEvent.getExecutionId());
		assertEquals(executionWithMessage.getProcessInstanceId(), messageEvent.getProcessInstanceId());
		assertEquals(processInstance.getProcessDefinitionId(), messageEvent.getProcessDefinitionId());
		assertEquals("messageName", messageEvent.getMessageName());
		assertNull(messageEvent.getMessageData());

		// Next, an signal-event is expected, as a result of the message
		assertTrue(listener.getEventsReceived().get(1) instanceof ActivitiSignalEvent);
		ActivitiSignalEvent signalEvent = (ActivitiSignalEvent) listener.getEventsReceived().get(1);
		assertEquals(ActivitiEventType.ACTIVITY_SIGNALED, signalEvent.getType());
		assertEquals("shipOrder", signalEvent.getActivityId());
		assertEquals(executionWithMessage.getId(), signalEvent.getExecutionId());
		assertEquals(executionWithMessage.getProcessInstanceId(), signalEvent.getProcessInstanceId());
		assertEquals(processInstance.getProcessDefinitionId(), signalEvent.getProcessDefinitionId());
		assertEquals("messageName", signalEvent.getSignalName());
		assertNull(signalEvent.getSignalData());
	}

	/**
	 * Test events related to message events, called from the API, targeting an event-subprocess.
	 */
	@Deployment
	public void testActivityMessageEventsInEventSubprocess() throws Exception {
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("messageProcess");
		assertNotNull(processInstance);

		Execution executionWithMessage = runtimeService.createExecutionQuery().activityId("shipOrder").singleResult();
		assertNotNull(executionWithMessage);

		runtimeService.messageEventReceived("messageName", executionWithMessage.getId());

		// Only a message-event should be present, no signal-event, since the
		// event-subprocess is
		// not signaled, but executed instead
		assertEquals(1, listener.getEventsReceived().size());

		// A message-event is expected
		assertTrue(listener.getEventsReceived().get(0) instanceof ActivitiMessageEvent);
		ActivitiMessageEvent messageEvent = (ActivitiMessageEvent) listener.getEventsReceived().get(0);
		assertEquals(ActivitiEventType.ACTIVITY_MESSAGE_RECEIVED, messageEvent.getType());
		assertEquals("catchMessage", messageEvent.getActivityId());
		assertEquals(executionWithMessage.getId(), messageEvent.getExecutionId());
		assertEquals(executionWithMessage.getProcessInstanceId(), messageEvent.getProcessInstanceId());
		assertEquals(processInstance.getProcessDefinitionId(), messageEvent.getProcessDefinitionId());
		assertEquals("messageName", messageEvent.getMessageName());
		assertNull(messageEvent.getMessageData());
	}
	
	/**
	 * Test events related to compensation events.
	 */
	@Deployment
	public void testActivityCompensationEvents() throws Exception {
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("compensationProcess");
		assertNotNull(processInstance);

		Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId())
				.singleResult();
		assertNotNull(task);
		
		// Complete task, next a compensation event will be thrown
		taskService.complete(task.getId());
		
		assertEquals(2, listener.getEventsReceived().size());

		// A compensate-event is expected
		assertTrue(listener.getEventsReceived().get(0) instanceof ActivitiActivityEvent);
		ActivitiActivityEvent activityEvent = (ActivitiActivityEvent) listener.getEventsReceived().get(0);
		assertEquals(ActivitiEventType.ACTIVITY_COMPENSATE, activityEvent.getType());
		assertEquals("compensate", activityEvent.getActivityId());
		// A new execution is created for the compensation-event, this should be visible in the event
		assertFalse(processInstance.getId().equals(activityEvent.getExecutionId()));
		assertEquals(processInstance.getProcessInstanceId(), activityEvent.getProcessInstanceId());
		assertEquals(processInstance.getProcessDefinitionId(), activityEvent.getProcessDefinitionId());
		
		// Also, a signal-event is received, representing the boundary-event being executed.
		assertTrue(listener.getEventsReceived().get(1) instanceof ActivitiSignalEvent);
		ActivitiSignalEvent signalEvent = (ActivitiSignalEvent) listener.getEventsReceived().get(1);
		assertEquals(ActivitiEventType.ACTIVITY_SIGNALED, signalEvent.getType());
		assertEquals("throwCompensation", signalEvent.getActivityId());
		assertEquals(processInstance.getId(), signalEvent.getExecutionId());
		assertEquals(processInstance.getProcessInstanceId(), signalEvent.getProcessInstanceId());
		assertEquals(processInstance.getProcessDefinitionId(), signalEvent.getProcessDefinitionId());
		assertEquals("compensationDone", signalEvent.getSignalName());
		assertNull(signalEvent.getSignalData());
	}

	@Override
	protected void initializeServices() {
		super.initializeServices();

		listener = new TestActivitiActivityEventListener();
		processEngineConfiguration.getEventDispatcher().addEventListener(listener);
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();

		if (listener != null) {
			listener.clearEventsReceived();
			processEngineConfiguration.getEventDispatcher().removeEventListener(listener);
		}
	}
}
