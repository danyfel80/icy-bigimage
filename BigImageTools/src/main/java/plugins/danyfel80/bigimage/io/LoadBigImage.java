package plugins.danyfel80.bigimage.io;

import java.awt.Rectangle;
import java.io.File;
import java.nio.file.Paths;

import algorithms.danyfel80.io.sequence.large.LargeSequenceImporter;
import icy.common.listener.DetailedProgressListener;
import icy.sequence.Sequence;
import icy.system.IcyHandledException;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzGroup;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;

/**
 * This plugin loads a large 2D image and shows the loaded sequence.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class LoadBigImage extends EzPlug implements Block, EzStoppable {

	// Image file path
	private EzVarFile file;

	// Resolution pyramid position
	private EzVarInteger resolutionLevel;

	// Sub-image retrieval settings
	private EzVarInteger rectangleX;
	private EzVarInteger rectangleY;
	private EzVarInteger rectangleW;
	private EzVarInteger rectangleH;

	// Progress listener
	private DetailedProgressListener progressEventHandler;

	// Block output
	private EzVarSequence resultSequenceVar;

	@Override
	protected void initialize() {
		file = new EzVarFile("Image path", "");
		resolutionLevel = new EzVarInteger("Resolution level", 0, 100, 1);
		resolutionLevel.setToolTipText("The resolution of the resulting image downsampled by powers of 2"
				+ " (e.g. 1 will import the image with half its size)");
		rectangleX = new EzVarInteger("X");
		rectangleY = new EzVarInteger("Y");
		rectangleW = new EzVarInteger("Width");
		rectangleH = new EzVarInteger("Height");

		addEzComponent(file);
		EzGroup resolutionGroup = new EzGroup("Resolution options", resolutionLevel);
		addEzComponent(resolutionGroup);
		resolutionGroup.setFoldedState(false);
		EzGroup tileGroup = new EzGroup("Load rectangle", rectangleX, rectangleY, rectangleW, rectangleH);
		addEzComponent(tileGroup);
		tileGroup.setFoldedState(false);

	}

	@Override
	public void declareInput(VarList inputMap) {
		file = new EzVarFile("Image path", "");
		resolutionLevel = new EzVarInteger("Resolution level", 0, 100, 1);
		resolutionLevel.setToolTipText("The resolution of the resulting image downsampled by powers of 2"
				+ " (e.g. 1 will import the image with half its size)");
		rectangleX = new EzVarInteger("X");
		rectangleY = new EzVarInteger("Y");
		rectangleW = new EzVarInteger("Width");
		rectangleH = new EzVarInteger("Height");

		inputMap.add(file.name, file.getVariable());
		inputMap.add(resolutionLevel.name, resolutionLevel.getVariable());
		inputMap.add(rectangleX.name, rectangleX.getVariable());
		inputMap.add(rectangleY.name, rectangleY.getVariable());
		inputMap.add(rectangleW.name, rectangleW.getVariable());
		inputMap.add(rectangleH.name, rectangleH.getVariable());
	}

	@Override
	public void declareOutput(VarList outputMap) {
		resultSequenceVar = new EzVarSequence("loaded sequence");
		outputMap.add(resultSequenceVar.name, resultSequenceVar.getVariable());
	}

	@Override
	protected void execute() {
		// Read input
		File filePath = file.getValue(true);
		int resolution = resolutionLevel.getValue(true);
		int x = rectangleX.getValue(true);
		int y = rectangleY.getValue(true);
		int w = rectangleW.getValue(true);
		int h = rectangleH.getValue(true);

		// Process
		long startTime = System.currentTimeMillis();
		LargeSequenceImporter importer = new LargeSequenceImporter();
		importer.setFilePath(Paths.get(filePath.toString()));
		importer.setTargetResolution(resolution);
		importer.setTargetPixelRectangle(new Rectangle(x, y, w, h));
		if (!isHeadLess()) {
			importer.addProgressListener(getProgressEventHandler());
		}

		Sequence result;
		try {
			result = importer.call();
		} catch (Exception e) {
			e.printStackTrace();
			throw new IcyHandledException(e);
		}
		if (!isHeadLess()) {
			importer.removeProgressListener(getProgressEventHandler());
		}
		long endTime = System.currentTimeMillis();
		long executionTime = endTime - startTime;

		// Set result
		if (isHeadLess()) {
			resultSequenceVar.setValue(result);
		} else {
			addSequence(result);
		}
		System.out.println(String.format("%s loaded in %d milliseconds.", filePath.toString(), executionTime));
	}

	private DetailedProgressListener getProgressEventHandler() {
		if (progressEventHandler == null) {
			progressEventHandler = (double progress, String message, Object data) -> {
				getUI().setProgressBarValue(progress);
				getUI().setProgressBarMessage(message);
				return false;
			};
		}

		return progressEventHandler;
	}

	@Override
	public void clean() {
	}

}
