package plugins.danyfel80.bigimage.io;

import java.awt.Rectangle;
import java.io.File;

import algorithms.danyfel80.io.image.big.BigImageImporter;
import icy.sequence.Sequence;
import plugins.adufour.ezplug.EzGroup;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;

/**
 * This plugin loads a big image and shows the loaded sequence.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class LoadBigImage extends EzPlug implements EzStoppable {

	private EzVarFile file;

	// Down-sampling
	private EzVarInteger resolutionLevel;

	// Tiling
	private EzVarInteger tileX;
	private EzVarInteger tileY;
	private EzVarInteger tileW;
	private EzVarInteger tileH;

	// Result
	private EzVarSequence loadedSequence;

	@Override
	protected void initialize() {
		file = new EzVarFile("Image path", "");
		resolutionLevel = new EzVarInteger("Resolution level", 0, 100, 1);
		resolutionLevel.setToolTipText("The resolution of the resulting image downsampled by powers of 2"
				+ " (e.g. 1 will import the image with half its size)");
		tileX = new EzVarInteger("Tile x");
		tileY = new EzVarInteger("Tile y");
		tileW = new EzVarInteger("Tile width");
		tileH = new EzVarInteger("Tile height");

		addEzComponent(file);
		EzGroup resolutionGroup = new EzGroup("Resolution options", resolutionLevel);
		addEzComponent(resolutionGroup);
		resolutionGroup.setFoldedState(false);
		EzGroup tileGroup = new EzGroup("Tile options", tileX, tileY, tileW, tileH);
		addEzComponent(tileGroup);
		tileGroup.setFoldedState(false);

		loadedSequence = new EzVarSequence("Sequence");
	}

	@Override
	protected void execute() {
		// Read input
		File filePath = file.getValue(true);
		int resolution = resolutionLevel.getValue(true);
		int x = tileX.getValue(true);
		int y = tileY.getValue(true);
		int w = tileW.getValue(true);
		int h = tileH.getValue(true);

		// Process
		long startTime = System.currentTimeMillis();
		BigImageImporter importer = new BigImageImporter(filePath, resolution, new Rectangle(x, y, w, h));
		importer.setProgressListener((double progress, String message, Object data) -> {
			getUI().setProgressBarValue(progress);
			getUI().setProgressBarMessage(message);
			return false;
		});
		Sequence result;
		try {
			result = importer.call();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		long endTime = System.currentTimeMillis();
		long executionTime = endTime - startTime;

		// Set result
		loadedSequence.setValue(result);
		addSequence(result);
		System.out.println(String.format("%s loaded in %d milliseconds.", filePath.toString(), executionTime));
	}

	@Override
	public void clean() {
	}

}
