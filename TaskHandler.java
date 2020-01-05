package app4mc.example.tool.java;

import java.math.BigInteger;

import org.eclipse.app4mc.amalthea.model.Amalthea;
import org.eclipse.app4mc.amalthea.model.HwModule;
import org.eclipse.app4mc.amalthea.model.HwStructure;
import org.eclipse.app4mc.amalthea.model.InterProcessStimulus;
import org.eclipse.app4mc.amalthea.model.PeriodicStimulus;
import org.eclipse.app4mc.amalthea.model.ProcessingUnit;
import org.eclipse.app4mc.amalthea.model.RelativePeriodicStimulus;
import org.eclipse.app4mc.amalthea.model.Stimulus;
import org.eclipse.app4mc.amalthea.model.Task;
import org.eclipse.app4mc.amalthea.model.Time;
import org.eclipse.app4mc.amalthea.model.TimeUnit;
import org.eclipse.app4mc.amalthea.model.util.DeploymentUtil;
import org.eclipse.app4mc.amalthea.model.util.FactoryUtil;
import org.eclipse.app4mc.amalthea.model.util.RuntimeUtil;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;

/*
 * Task Handler class that performs many task related operations like
 *
 * 1. Getting periodicity of a task
 * 2. Getting the Execution time for a task
 * 3. Getting the response time for a task
 *
 */
public class TaskHandler {

	private final EList<Task> taskList;
	EList<ProcessingUnit> pUnit = null;

	/*
	 * Constructor that accepts processingUnit and taskList
	 */
	public TaskHandler(final EList<Task> taskList) {
		this.taskList = taskList;
	}

	/*
	 * Function that returns the periodicity of a task
	 *
	 * Param - @ Task Return - @ Time
	 *
	 */
	public Time getPeriodicity(final Task task) {
		Time periodic = FactoryUtil.createTime();
		periodic.setUnit(TimeUnit.US);
		final EList<Stimulus> stimuli = task.getStimuli();
		final Stimulus firstInstance = stimuli.get(0);
		if (firstInstance instanceof PeriodicStimulus) {
			periodic = ((PeriodicStimulus) firstInstance).getRecurrence();
		}
		else if (firstInstance instanceof InterProcessStimulus) {
			final PeriodicStimulus p = (PeriodicStimulus) ((InterProcessStimulus) firstInstance).getExplicitTriggers()
					.get(0).getContainingProcess().getStimuli().get(0);
			periodic = p.getRecurrence();
		}
		else if (firstInstance instanceof RelativePeriodicStimulus) {
			periodic = ((RelativePeriodicStimulus) firstInstance).getNextOccurrence().getAverage();
		}
		return adjustTimeUnit(periodic);
	}

	/*
	 * Function that returns the WCET of a task
	 *
	 * Param - @ Task Return - @ Time
	 *
	 */
	public Time getTaskExecutionTime(final Task task) {
		final Time execTime = RuntimeUtil.getExecutionTimeForProcess(task, getTaskCore(task), null,
				RuntimeUtil.TimeType.WCET);
		return adjustTimeUnit(execTime);
	}

	/*
	 * Function that Computes the WCET of a task
	 *
	 * Param - @ Task Return - @ Time
	 *
	 */
	public Time getResponseTime(final Task t) {
		final EList<Task> HPTList = getHPTList(t);
		long temp = 0;
		long multemp = 1;
		final Time respTime = getTaskExecutionTime(t);
		if (HPTList.size() == 1) {
			return respTime;
		}
		do {
			for (final Task task : HPTList) {
				if (task != t) {

					multemp = temp;
					temp = getTaskExecutionTime(t).getValue().longValue() + computeInterference(multemp, HPTList, t);
					respTime.setValue(BigInteger.valueOf(temp));
					respTime.setUnit(getTaskExecutionTime(task).getUnit());
				}
			}
		} while (temp != multemp && temp <= getPeriodicity(t).getValue().longValue());

		if (temp > getPeriodicity(t).getValue().longValue()) {
			System.out.println("Response time " + respTime + " exceeds periodicity " + getPeriodicity(t));
		}
		return respTime;
	}

	private Time adjustTimeUnit(final Time respTime) {
		if (respTime.getUnit() == TimeUnit.PS) {
			respTime.setValue(BigInteger.valueOf(respTime.getValue().longValue() / 1000000));
		}
		else if (respTime.getUnit() == TimeUnit.NS) {
			respTime.setValue(BigInteger.valueOf(respTime.getValue().longValue() / 1000));
		}
		else if (respTime.getUnit() == TimeUnit.MS) {
			respTime.setValue(BigInteger.valueOf(respTime.getValue().longValue() * 1000));
		}
		respTime.setUnit(TimeUnit.US);
		return respTime;
	}

	/*
	 * Function that Computes the Interference of other task on one task
	 *
	 * Param - @ Task - @ double temp data - @ EList<Task> List of Higher
	 * priority tasks Return - @ long
	 *
	 */
	private long computeInterference(final double multemp, final EList<Task> HPTList, final Task t) {
		long temp = 0;
		for (final Task task : HPTList) {
			if (task != t) {
				temp = (long) (temp + Math.ceil(multemp / getPeriodicity(task).getValue().longValue())
						* getTaskExecutionTime(task).getValue().longValue());
			}
		}
		return temp;

	}

	/*
	 * Function that gives a list of Higher priority tasks more than itself
	 *
	 * Param - @ Task Return - @ EList<Task> List of Higher priority tasks
	 *
	 */

	private EList<Task> getHPTList(final Task t) {
		final EList<Task> HPTList = new BasicEList<Task>();
		for (final Task task : getCoreAssignedTasks(getTaskCore(t))) {
			if (getPeriodicity(task).getValue().longValue() <= getPeriodicity(t).getValue().longValue()) {
				HPTList.add(task);
			}
		}
		return HPTList;
	}

	public EList<Task> getCoreAssignedTasks(final ProcessingUnit core) {
		final EList<Task> taskList = new BasicEList<Task>();
		for (final Task task : this.taskList) {
			if (getTaskCore(task) == core) {
				taskList.add(task);
			}
		}
		return taskList;
	}

	private ProcessingUnit getTaskCore(final Task t) {
		EList<ProcessingUnit> pu = new BasicEList<ProcessingUnit>();
		if (((Amalthea) t.eContainer().eContainer()).getMappingModel() != null) {
			if (((Amalthea) t.eContainer().eContainer()).getMappingModel().getTaskAllocation() != null) {
				return DeploymentUtil.getTaskAllocations(t, (Amalthea) t.eContainer().eContainer()).get(0).getAffinity()
						.get(0);
			}
		}
		final Amalthea model = (Amalthea) t.eContainer().eContainer();
		pu = getPuHwModel(model);
		if (pu.size() != 1) {
			System.out.println("Number of Cores are " + this.pUnit.size() + " Returning only 1st core");
		}
		if (pu.size() == 0) {
			System.out.println("No cores found in hw model");
			return null;
		}
		return pu.get(0);
	}

	public EList<ProcessingUnit> getPuHwModel(final Amalthea model) {
		for (final HwStructure struct : model.getHwModel().getStructures()) {
			getModelProcessingUnit(struct);
		}
		return this.pUnit;
	}

	private void getModelProcessingUnit(final HwStructure hwStruc) {
		// model.getHwModel().getStructures().get(0).getStructures().get(0).getStructures().get(0).getModules().get(1);

		final EList<HwStructure> structures = hwStruc.getStructures();
		final EList<HwModule> modules = hwStruc.getModules();
		for (final HwModule module : modules) {
			if (module instanceof ProcessingUnit) {
				this.pUnit = new BasicEList<ProcessingUnit>();
				this.pUnit.add((ProcessingUnit) module);
			}
		}
		if (this.pUnit == null) {
			for (final HwStructure structure : structures) {
				getModelProcessingUnit(structure);
			}
		}

	}

	/*
	 * Function that prints the response times for all tasks from task list
	 *
	 * Param - None Return - None
	 *
	 */
	public void printResponseTimeTaskList() {
		System.out.println("Response Times");
		System.out.println("------------------------------------------------------------------------");
		for (final Task t : this.taskList) {
			System.out.println("Task Name \t :\t" + t.getName());
			System.out.println("Response Time \t :\t" + getResponseTime(t));
			System.out.println();
		}
		System.out.println("------------------------------------------------------------------------");
	}

}
