package algorithms.danyfel80.bigimage.segmentation;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;

import algorithms.danyfel80.bigimage.io.BigImageReader;
import algorithms.danyfel80.bigimage.io.BigImageUtil;
import algorithms.danyfel80.bigimage.io.BigImageWriter;
import icy.common.exception.UnsupportedFormatException;
import icy.common.listener.RichProgressListener;
import icy.image.lut.LUT;
import icy.sequence.Sequence;
import icy.sequence.SequenceDataIterator;
import icy.sequence.SequenceUtil;
import icy.type.DataType;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import plugins.adufour.thresholder.KMeans;
import plugins.kernel.importer.LociImporterPlugin;

/**
 * This class performs class thresholding on big images using Alexandre
 * Adufour's thresholder plugin on a down-sampled version of the full-size
 * image.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class BigImageThresholder implements Runnable {

	/**
	 * Input file path.
	 */
	private final File inputPath;
	/**
	 * Classes used for segmentation.
	 */
	private final int[] usedClasses;
	/**
	 * Output file path.
	 */
	private File outputPath;
	/**
	 * Progress listener.
	 */
	private final RichProgressListener listener;

	/**
	 * Flag for processing interruption
	 */
	private boolean isInterrupted;
	/**
	 * Processed tiles.
	 */
	private int numProcessedTiles;
	/**
	 * Total processed tiles.
	 */
	private int totalProcessedTiles;
	/**
	 * Image size.
	 */
	private Dimension imgSize;
	/**
	 * Image amount of channels.
	 */
	private int imgSizeC;
	/**
	 * Image data type.
	 */
	private DataType imgDataType;

	/**
	 * image reader runnable
	 */
	private BigImageReader biReader;
	/**
	 * image reader thread
	 */
	private Thread biReaderThread;
	/**
	 * Image threshold processing thread pool.
	 */
	private ExecutorService threadPool;
	/**
	 * Writer for threshold result.
	 */
	private BigImageWriter writer;

	/**
	 * @param inputPath
	 *          Input file path.
	 * @param numClasses
	 *          Amount of classes to threshold.
	 * @param outputPath
	 *          Output file path.
	 * @throws IllegalArgumentException
	 *           If any of the parameters is not correct.
	 */
	public BigImageThresholder(File inputPath, int[] usedClasses, File outputPath, RichProgressListener listener)
	    throws IllegalArgumentException {
		if (inputPath == null || !inputPath.exists()) {
			throw new IllegalArgumentException("Invalid input file path: " + inputPath);
		}
		this.inputPath = inputPath;

		if (usedClasses.length < 2) {
			throw new IllegalArgumentException("The amount of classes must be at least 2: " + usedClasses.length);
		}
		
		this.usedClasses = usedClasses;

		this.outputPath = outputPath;

		this.listener = listener;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		this.isInterrupted = false;

		try {
			biReader = new BigImageReader(inputPath, null, 2000, 2000, new RichProgressListener() {

				@Override
				public boolean notifyProgress(double position, double length, String message, Object data) {
					if (listener != null) {
						listener.notifyProgress((position / length) * 0.33, 1.0, message + " (tile " + position + "/" + length + ")",
			          null);
					}
					return true;
				}
			});
		} catch (IllegalArgumentException | UnsupportedFormatException | IOException e) {
			e.printStackTrace();
			return;
		}

		this.biReaderThread = new Thread(biReader);
		biReaderThread.start();
		try {
			biReaderThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		}
		biReaderThread = null;
		if (this.isInterrupted) {
			return;
		}

		if (listener != null) {
			listener.notifyProgress(0.33, 1.0, "Computing Threshold", null);
		}

		Sequence seq = biReader.getSequence();
		biReader = null;

		// Convert to gray
		Sequence graySeq = SequenceUtil.convertColor(seq, BufferedImage.TYPE_BYTE_GRAY, seq.createCompatibleLUT());

		// Find threshold
		List<Double> thresh = getThresholdValues(graySeq);
		if (listener != null) {
			listener.notifyProgress(0.67, 1.0, "Saving result", null);
		}
		System.out.print("Threshold values = " + thresh + "\nUsing classes =");
		for (int i = 0; i < usedClasses.length; i++) {
			System.out.print(" " + usedClasses[i]);
		}
		System.out.println("");

		if (this.isInterrupted) {
			return;
		}
		// Perform thresholding
		imgSize = null;
		imgSizeC = 0;
		imgDataType = null;
		try {
			imgSize = BigImageUtil.getImageDimension(inputPath);
			imgSizeC = BigImageUtil.getImageChannelCount(inputPath);
			imgDataType = BigImageUtil.getImageDataType(inputPath);
		} catch (IOException | UnsupportedFormatException e) {
			e.printStackTrace();
			return;
		}

		Rectangle tileInfo = computeTileSize(imgSize, imgSizeC, imgDataType);

		this.totalProcessedTiles = tileInfo.x * tileInfo.y;
		System.out.println(
		    "" + totalProcessedTiles + " tiles of " + tileInfo.width + " by " + tileInfo.height + " for " + imgSize);

		try {
			String inPathString = inputPath.getAbsolutePath();
			String outPathString;
			try {
				outPathString = outputPath.getAbsolutePath();
			} catch (NullPointerException e) {
				outPathString = FilenameUtils.getFullPath(inPathString) + FilenameUtils.getBaseName(inPathString)
				    + "_Threshold(" + thresh + ").tiff";
				outputPath = new File(outPathString);
			}
			this.writer = new BigImageWriter(outputPath, imgSize, 1, DataType.UBYTE, tileInfo.getSize());
		} catch (ServiceException | FormatException | IOException e1) {
			e1.printStackTrace();
			return;
		}

		// Announce loading start
		if (this.listener != null) {
			this.listener.notifyProgress(0.67 + 0.33 * (numProcessedTiles / totalProcessedTiles), 1.0,
			    "Saving result(" + numProcessedTiles + "/" + totalProcessedTiles + ")", null);
		}
		this.threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1);

		// For each tile in y
		for (int j = 0; j < tileInfo.y; j++) {
			// set tile height
			int currentTileHeight = (j == tileInfo.y - 1) ? (imgSize.height - j * tileInfo.height) : tileInfo.height;
			// For each tile in x
			for (int i = 0; i < tileInfo.x; i++) {
				// set tile width
				int currentTileWidth = (i == tileInfo.x - 1) ? (imgSize.width - i * tileInfo.width) : tileInfo.width;

				// set tile rectangle
				Dimension currentTileDimension = new Dimension(currentTileWidth, currentTileHeight);
				Point currentTilePosition = new Point(i * tileInfo.width, j * tileInfo.height);
				Rectangle currentTileRectangle = new Rectangle(currentTilePosition, currentTileDimension);

				threadPool.submit(new TileThresholdingTask(currentTileRectangle, thresh, usedClasses));
			}
		}

		threadPool.shutdown();
		try {
			threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		} finally {
			try {
				writer.closeWriter();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}
	}

	/**
	 * Performa a KMeans on the specified sequence and returns the maximum class
	 * division.
	 * 
	 * @param graySeq
	 *          Sequence to classify
	 * @return Maximum class division.
	 */
	private List<Double> getThresholdValues(Sequence graySeq) {
		double[][] thrs = KMeans.computeKMeansThresholds(graySeq, usedClasses.length);
		List<Double> l = Arrays.asList(ArrayUtils.toObject(thrs[0]));
		Collections.sort(l);
		return l;
	}

	/**
	 * Computes the tile size and amount in x and y
	 * 
	 * @param imgSize
	 *          Size of the image
	 * @param imgSizeC
	 *          Channel size of the image
	 * @param imgDataType
	 *          Data type of the image
	 * @return Rectangle with tile quantities in the position attribute and tile
	 *         dimensions.
	 */
	private Rectangle computeTileSize(Dimension imgSize, int imgSizeC, DataType imgDataType) {
		long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		long ramAvailable = Runtime.getRuntime().maxMemory() - usedMem;
		int processors = Runtime.getRuntime().availableProcessors();

		Dimension loadTileSize = new Dimension(imgSize);
		Dimension outTileSize = new Dimension(imgSize);
		Point loadTileCount = new Point(1, 1);

		long loadTileDataSize = (long) imgSizeC * (long) imgDataType.getSize() * (long) loadTileSize.width
		    * (long) loadTileSize.height;
		loadTileDataSize += (long) imgSizeC * (long) imgDataType.getSize() * (long) outTileSize.width
		    * (long) outTileSize.height;

		// Compute tile size according to the processor quantity and available ram
		while (ramAvailable / (2 * processors) < loadTileDataSize && (loadTileSize.width > 7 || loadTileSize.height > 7)
		    && (outTileSize.width > 1 && outTileSize.height > 1)) {
			if (loadTileSize.width > 7) {
				loadTileSize.width /= 2;
				outTileSize.width /= 2;
				loadTileCount.x *= 2;
			}
			if (loadTileSize.height > 7) {
				loadTileSize.height /= 2;
				outTileSize.height /= 2;
				loadTileCount.y *= 2;
			}

			loadTileDataSize = (long) imgSizeC * (long) imgDataType.getSize() * (long) loadTileSize.width
			    * (long) loadTileSize.height;
			loadTileDataSize += (long) imgSizeC * (long) imgDataType.getSize() * (long) outTileSize.width
			    * (long) outTileSize.height;
		}
		// Add additional tile if tile division is not exact
		if (imgSize.width % loadTileSize.width != 0) {
			loadTileCount.x++;
		}
		if (imgSize.height % loadTileSize.height != 0) {
			loadTileCount.y++;
		}

		return new Rectangle(loadTileCount, loadTileSize);
	}

	/**
	 * This class is used to parallelize the thresholding procedure.
	 * 
	 * @author Daniel Felipe Gonzalez Obando
	 */
	class TileThresholdingTask implements Runnable {

		private final Rectangle tileRectangle;
		private final List<Double> thresh;
		private final int[] usedClasses;

		/**
		 * Constructor
		 * 
		 * @param tileRectangle
		 *          Tile information
		 * @param thresh
		 *          Threshold value
		 */
		public TileThresholdingTask(Rectangle tileRectangle, List<Double> thresh, int[] usedClasses) {
			this.tileRectangle = tileRectangle;
			this.thresh = thresh;
			this.usedClasses = usedClasses;
		}

		@Override
		public void run() {
			// System.out.println("executing " + tileRectangle);
			long freeMemory = Runtime.getRuntime().maxMemory()
			    - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
			long neededMemory = (long) imgSizeC * (long) imgDataType.getSize() * (long) tileRectangle.width
			    * (long) tileRectangle.height;
			neededMemory *= 2l;
			while (freeMemory < neededMemory) {
				try {
					System.out.println("Waiting to free memory");
					this.wait(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
					return;
				}
				freeMemory = Runtime.getRuntime().maxMemory()
				    - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
			}

			// Open reader
			LociImporterPlugin importer = new LociImporterPlugin();
			try {
				importer.open(inputPath.getAbsolutePath(), 0);

				// Read tile
				Sequence resultSeq = new Sequence(importer.getImage(0, 0, tileRectangle, 0, 0));

				// Convert to gray
				LUT lut = resultSeq.createCompatibleLUT();
				resultSeq = SequenceUtil.convertColor(resultSeq, BufferedImage.TYPE_BYTE_GRAY, lut);
				
				resultSeq.beginUpdate();
				SequenceDataIterator it = new SequenceDataIterator(resultSeq);
				while (!it.done()) {
					double floorVal = 0;
					double ceilVal = 0;
					boolean useIt = false;
					for (int i = 0; i < usedClasses.length; i++) {
						if (i > 0) {
							floorVal = thresh.get(i-1);
						}
						if (i < usedClasses.length-1) {
							ceilVal = thresh.get(i);
						} else {
							ceilVal = resultSeq.getDataTypeMax()+1;
						}
						
						if (usedClasses[i] != 0 && it.get() >= floorVal && it.get() < ceilVal) {
							useIt = true;
							break;
						}
					}
					it.set((useIt)? resultSeq.getDataTypeMax(): 0);
					it.next();
				}
				resultSeq.endUpdate();

				// Copy data to full result
				writer.saveTile(resultSeq.getFirstImage(), tileRectangle.getLocation());

			} catch (ClosedByInterruptException e) {
				return;
			} catch (UnsupportedFormatException | IOException | FormatException e) {
				e.printStackTrace();
			} finally {
				try {
					importer.close();
				} catch (IOException e) {
					e.printStackTrace();
					return;
				} finally {
					synchronized (BigImageThresholder.this) {
						BigImageThresholder.this.numProcessedTiles++;
						if (numProcessedTiles % (Runtime.getRuntime().availableProcessors()) == 0) {
							Runtime.getRuntime().gc();
						}
						if (BigImageThresholder.this.listener != null) {
							BigImageThresholder.this.listener.notifyProgress(
							    0.67 + 0.33 * ((double) BigImageThresholder.this.numProcessedTiles
							        / (double) BigImageThresholder.this.totalProcessedTiles),
							    1.0, "Saving result (tile " + numProcessedTiles + "/" + totalProcessedTiles + ")", null);
						}

						if (BigImageThresholder.this.isInterrupted) {
							BigImageThresholder.this.threadPool.shutdownNow();
							return;
						}
					}
				}
			}
		}

	}

	public void interrupt() {
		this.isInterrupted = true;
		if (this.biReaderThread != null && this.biReaderThread.isAlive()) {
			this.biReader.interrupt();
		}
	}

}
