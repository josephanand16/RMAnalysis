package app4mc.example.tool.java;

import java.io.File;

import org.eclipse.app4mc.amalthea.model.Amalthea;
import org.eclipse.app4mc.amalthea.model.HwModule;
import org.eclipse.app4mc.amalthea.model.HwStructure;
import org.eclipse.app4mc.amalthea.model.ProcessingUnit;
import org.eclipse.app4mc.amalthea.model.Task;
import org.eclipse.app4mc.amalthea.model.Time;
import org.eclipse.app4mc.amalthea.model.io.AmaltheaLoader;
import org.eclipse.app4mc.amalthea.model.io.AmaltheaWriter;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;

/*
 * Tester Function that takes the amalthea model as input
 * and computes Proc Utilization and checks if a given set of tasks
 * are Schedulable or Not
 *
 *
 */

public class RateMonotonicScheduling {
	static EList<ProcessingUnit> pUnit = new BasicEList<ProcessingUnit>();

	public static EList<ProcessingUnit> getPuHwModel(final Amalthea model) {
		for (final HwStructure struct : model.getHwModel().getStructures()) {
			getModelProcessingUnit(struct);
		}
		return pUnit;
	}

	private static void getModelProcessingUnit(final HwStructure hwStruc) {

		BasicEList<ProcessingUnit> pUnitNew = null;
		final EList<HwStructure> structures = hwStruc.getStructures();
		final EList<HwModule> modules = hwStruc.getModules();
		for (final HwModule module : modules) {
			if (module instanceof ProcessingUnit) {
				pUnitNew = new BasicEList<ProcessingUnit>();
				pUnitNew.add((ProcessingUnit) module);
				pUnit.add((ProcessingUnit) module);
			}
		}
		if (pUnitNew == null) {
			for (final HwStructure structure : structures) {
				getModelProcessingUnit(structure);
			}
		}

	}

	public static void calcProcUtil(final TaskHandler th, final Amalthea model) {
		double bounds;
		for (final ProcessingUnit core : getPuHwModel(model)) {
			double sum = 0.0;
			final double size = th.getCoreAssignedTasks(core).size();
			System.out.println("Processor Core \t: " + core.getDefinition().getName() + " " + core.getName());
			System.out.println("------------------------------------------------------------------------");
			if (size > 0) {
				bounds = (size * ((Math.pow(2, 1 / size)) - 1)) * 100;
				System.out.printf("RM Bounds for given tasks : \t%.2f", bounds);
				System.out.print(" %\n\n");
				System.out.println("------------------------------------------------------------------------");
				for (final Task task : th.getCoreAssignedTasks(core)) {
					final Time taskTime = th.getTaskExecutionTime(task).adjustUnit();
					final Time periodic = th.getPeriodicity(task).adjustUnit();
					System.out.println("Task Name : \t\t\t" + task.getName());
					System.out.println("Periodicity : \t\t\t" + periodic);
					System.out.println("WCET : \t\t\t\t" + taskTime);
					System.out.printf("CPU utilization in : \t\t%.2f ", (taskTime.divide(periodic)) * 100);
					System.out.print("%\n\n");
					System.out.println("------------------------------------------------------------------------");
					sum = sum + taskTime.divide(periodic);
				}
				final double procUtil = sum * 100;
				System.out.printf("Total Processor Utilization : \t%.2f", procUtil);
				System.out.print(" %\n\n");
				System.out.println("------------------------------------------------------------------------");
				System.out.print("Verdict : ");
				if (procUtil > bounds && procUtil <= 100) {
					System.out.println(
							"Tasks are Schedulable, but response times of tasks with larger periodicity depends on other tasks ");
				}
				else if (procUtil > 100) {
					System.out.println("Tasks are not Schedulable, Response times exceed the periodicity ");
				}
				else if (procUtil < bounds) {
					System.out.println("Tasks are RM Schedulable ");
				}
			}
			else {
				System.out.println("No tasks mapped to core");
			}
			System.out.println("========================================================================");
			System.out.println();
			System.out.println();

		}
	}

	public static void main(final String[] args) {
		final File inputFile = new File("model-input/ChallengeModel_release_rtso.amxmi");
		final File outputFile = new File("model-output/ChallengeModel_release_rtso_1.amxmi");

		// ***** Load *****
		final Amalthea model = AmaltheaLoader.loadFromFile(inputFile);
		// ***Code Start****
		System.out.println("========================================================================");
		System.out.println("	Response Time Analysis for Rate Monotonic Scheduling	");
		System.out.println("========================================================================");
		System.out.println("\n");
		final EList<Task> taskList = model.getSwModel().getTasks();
		final TaskHandler th = new TaskHandler(taskList);
		calcProcUtil(th, model);
		th.printResponseTimeTaskList();

		// ***** Save *****

		AmaltheaWriter.writeToFile(model, outputFile);
		System.out.println("done");
	}

}
