/*
 * Copyright 2010-2016 Institut Pasteur.
 * 
 * This file is part of Icy.
 * 
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <http://www.gnu.org/licenses/>.
 */
package algorithms.danyfel80.bigimage.io;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FilenameUtils;

import icy.common.exception.UnsupportedFormatException;
import icy.common.listener.RichProgressListener;
import icy.file.Saver;
import icy.sequence.Sequence;
import impl.danyfel80.sequence.volume.icyBufferedImage.util.IcyBufferedImageCursor;

/**
 * Big image tile slicer. Takes an image file and slices it in smaller image
 * tiles.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class BigImageTileSlicer implements Callable<Void> {

	/**
	 * Task to process each tile of the big image.
	 * 
	 * @author Daniel Felipe Gonzalez Obando
	 */
	public class TileProcessingTask implements Callable<Boolean> {

		Rectangle tileBounds;

		/**
		 * TODO
		 */
		public TileProcessingTask(Rectangle bounds) {
			this.tileBounds = bounds;
		}

		/*
		 * (non-Javadoc)
		 * @see java.util.concurrent.Callable#call()
		 */
		@Override
		public Boolean call() throws Exception {

			BigImageReader imageReader = new BigImageReader(inputFile.toFile(), tileBounds, 0, 0, null);
			imageReader.run();

			Sequence tileResult = imageReader.getSequence();
			int sizeC = tileResult.getSizeC();

			double[] meanVariation = new double[sizeC];
			int items = 0;

			IcyBufferedImageCursor cursor = new IcyBufferedImageCursor(tileResult, 0, 0);
			for (int x = 0; x < tileBounds.width; x++) {
				for (int y = 0; y < tileBounds.height; y++) {
					for (int c = 0; c < sizeC; c++) {
						double value = cursor.get(x, y, c) - backgroundColor[c];
						meanVariation[c] += ((value * value) - meanVariation[c]) / ++items;
					}
				}
			}

			System.out.println(Arrays.toString(meanVariation));

			boolean isAboveThreshold = Arrays.stream(meanVariation).mapToObj(x -> x > contentThreshold * contentThreshold)
					.reduce((prev, curr) -> prev || curr).get();
			System.out.println(isAboveThreshold);
			if (isAboveThreshold) {
				String fileName = FilenameUtils.getBaseName(inputFile.toString());
				Saver.save(tileResult,
						Paths.get(outputDirectory.toString(), fileName + "_Tile(" + tileBounds.x + "," + tileBounds.y + ").tif")
								.toFile());
				return true;
			}
			return false;
		}

	}

	private Path			inputFile;
	private Path			outputDirectory;
	private Dimension	tileSize;
	private double[]	backgroundColor;
	private double		contentThreshold;

	List<RichProgressListener> progressListeners;

	/**
	 * TODO
	 * 
	 * @param inputFile
	 * @param outputDirectory
	 * @param tileSize
	 * @param backgroundColor
	 * @param contentThreshold
	 */
	public BigImageTileSlicer(Path inputFile, Path outputDirectory, Dimension tileSize, double[] backgroundColor,
			double contentThreshold) throws IllegalArgumentException {
		super();

		validateParameters();
		this.inputFile = inputFile;
		this.outputDirectory = outputDirectory;
		this.tileSize = tileSize;
		this.backgroundColor = backgroundColor;
		this.contentThreshold = contentThreshold;
		this.progressListeners = new ArrayList<>(2);
	}

	/**
	 * TODO
	 * 
	 * @throws IllegalArgumentException
	 */
	private void validateParameters() throws IllegalArgumentException {
		// TODO
	}

	/**
	 * TODO
	 * 
	 * @param listener
	 */
	public void addProgressListener(RichProgressListener listener) {
		this.progressListeners.add(listener);
	}

	/**
	 * @throws IOException
	 * @throws UnsupportedFormatException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public void execute() throws IOException, UnsupportedFormatException, InterruptedException, ExecutionException {
		Dimension sequenceDimension = BigImageUtil.getImageDimension(inputFile.toFile());

		Dimension tileAmount = new Dimension(sequenceDimension.width / tileSize.width,
				sequenceDimension.height / tileSize.height);
		if (tileAmount.width * tileSize.width < sequenceDimension.width) {
			tileAmount.width++;
		}
		if (tileAmount.height * tileSize.height < sequenceDimension.height) {
			tileAmount.height++;
		}
		int numTiles = tileAmount.width * tileAmount.height;

		System.out.println("Tiles to process = " + tileAmount + " = " + numTiles);
		int tilesSaved = 0;
		final AtomicInteger processedTiles = new AtomicInteger(0);
		ThreadPoolExecutor tpTiles = (ThreadPoolExecutor) Executors
				.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
		ExecutorCompletionService<Boolean> csTiles = new ExecutorCompletionService<>(tpTiles);

		for (int xTile = 0; xTile < sequenceDimension.getWidth(); xTile += tileSize.width) {
			for (int yTile = 0; yTile < sequenceDimension.getHeight(); yTile += tileSize.height) {
				Dimension currentTileDimension = new Dimension(tileSize);
				if (xTile + currentTileDimension.width > sequenceDimension.width) {
					currentTileDimension.width = sequenceDimension.width - xTile;
				}

				if (yTile + currentTileDimension.height > sequenceDimension.height) {
					currentTileDimension.height = sequenceDimension.height - yTile;
				}

				csTiles.submit(new TileProcessingTask(new Rectangle(new Point(xTile, yTile), currentTileDimension)));
			}
		}
		tpTiles.shutdown();

		for (int i = 0; i < numTiles; i++) {
			try {
				boolean tileSaved = csTiles.take().get();
				if (tileSaved) {
					tilesSaved++;
				}
				processedTiles.incrementAndGet();

				progressListeners.forEach(l -> {
					l.notifyProgress(processedTiles.get(), numTiles,
							"Processing Tiles (" + processedTiles.get() + "/" + numTiles + ")", null);
				});
			} catch (InterruptedException | ExecutionException e) {
				tpTiles.shutdownNow();
				tpTiles.awaitTermination(3, TimeUnit.SECONDS);
				throw e;
			}
		}

		System.out.println("Total tiles saved = " + tilesSaved);
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.concurrent.Callable#call()
	 */
	@Override
	public Void call() throws Exception {
		execute();
		return null;
	}
}
