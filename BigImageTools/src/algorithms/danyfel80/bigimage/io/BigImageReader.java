package algorithms.danyfel80.bigimage.io;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;

import icy.common.exception.UnsupportedFormatException;
import icy.common.listener.ProgressListener;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.sequence.Sequence;
import icy.type.DataType;
import plugins.kernel.importer.LociImporterPlugin;

/**
 * This class allows to load and down-sample big images using parallel
 * techniques.
 * 
 * @author Daniel Felipe Gonzalez Obando
 */
public class BigImageReader implements Runnable {

	/**
	 * Path of the file to load.
	 */
	private final File path;
	/**
	 * The tile to be loaded from the image file.
	 */
	private final Rectangle tile;
	/**
	 * The maximum width of the loaded image.
	 */
	private final int maxWidth;
	/**
	 * The maximum height of the loaded image.
	 */
	private final int maxHeight;
	/**
	 * Listener to manage progress events.
	 */
	private final ProgressListener listener;
	/**
	 * The size of the image to load.
	 */
	private Dimension inSize;
	/**
	 * The size of the resulting image.
	 */
	private Dimension outSize;
	/**
	 * The amount of channels in the image file.
	 */
	private int channelSize;
	/**
	 * The type of data of the image in the file.
	 */
	private DataType dataType;
	/**
	 * Amount of tiles already loaded.
	 */
	private int numProcessedTiles;
	/**
	 * Total amount of tiles needed to load the result.
	 */
	private int totalProcessedTiles;
	/**
	 * Flag to indicate interruption on thread.
	 */
	private boolean isInterrupted;
	/**
	 * Flag to indicate immediate interruption.
	 */
	private boolean isShutdownNow;
	/**
	 * The threadpool for threaded image loading.
	 */
	private ExecutorService threadPool;
	/**
	 * Resulting sequence.
	 */
	private Sequence loadedSequence;

	/**
	 * Constructor for the image reader. The specified image will extract the
	 * specified tile scaled by a power of 2 to fit the specified maximum size.
	 * 
	 * @param path
	 *          Path of the file to load.
	 * @param tile
	 *          Tile to extract from image file. If the tile parameter is null,
	 *          then the entire image will be loaded.
	 * @param maxWidth
	 *          Max width of the resulting sequence. If maxWidth is 0, then the
	 *          resulting image will have the original size.
	 * @param maxHeight
	 *          Max height of the resulting sequence. If maxHeight is 0, then the
	 *          resulting image will have the original size.
	 * @param listener
	 *          Listener to manage progress events.
	 * @throws IllegalArgumentException
	 *           If any of the parameters is not correct.
	 * @throws IOException
	 *           If the file doesn't exist.
	 * @throws UnsupportedFormatException
	 *           If the file is unreadable.
	 */
	public BigImageReader(final File path, final Rectangle tile, final int maxWidth, final int maxHeight,
	    final ProgressListener listener) throws IllegalArgumentException, UnsupportedFormatException, IOException {
		// Check file existence
		if (path.exists()) {
			this.path = path;
		} else {
			throw new IllegalArgumentException("File " + path + " doesn't exist.");
		}
		
		Dimension dims = BigImageUtil.getImageDimension(this.path);
		
		// Check tile size
		if (tile == null) {
			this.tile = new Rectangle(new Point(0, 0), dims);
		} else if (tile.isEmpty()) {
			System.err.println(dims);
			throw new IllegalArgumentException("Tile to extract cannot be empty.");
		} else {
			System.err.println(dims);
			if (tile.x >= dims.width || tile.y >= dims.height) {
				throw new IllegalArgumentException("Tile to extract cannot be placed out of image bounds: " + tile.getLocation());
			}
			if (tile.x < 0){
				tile.width += tile.x;
				tile.x = 0;
			}
			if (tile.y < 0){
				tile.height += tile.y;
				tile.y = 0;
			}
			if (tile.x + tile.width > dims.width){
				tile.width -= tile.x + tile.width - dims.width;
			}
			if (tile.y + tile.height > dims.height){
				tile.height -= tile.y + tile.height - dims.height;
			}
			this.tile = tile;
		}

		// Check output size
		if (maxWidth == 0) {
			this.maxWidth = this.tile.width;
		} else {
			this.maxWidth = maxWidth;
		}

		if (maxHeight == 0) {
			this.maxHeight = this.tile.height;
		} else {
			this.maxHeight = maxHeight;
		}

		// Assign the progress event listener
		this.listener = listener;

		// Initialize loaded sequence to null
		this.loadedSequence = null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		this.loadedSequence = null;
		this.numProcessedTiles = 0;
		this.isInterrupted = false;
		this.isShutdownNow = false;
		
		this.inSize = new Dimension(tile.getSize());
		this.channelSize = 0;
		try {
			channelSize = BigImageUtil.getImageChannelCount(path);
		} catch (UnsupportedFormatException | IOException e) {
			e.printStackTrace();
			return;
		}
		this.dataType = null;
		try {
			dataType = BigImageUtil.getImageDataType(path);
		} catch (UnsupportedFormatException | IOException e) {
			e.printStackTrace();
			return;
		}

		this.outSize = new Dimension(inSize);
		double scale = 1;

		while (outSize.width > maxWidth && outSize.height > maxHeight) {
			outSize.width /= 2;
			outSize.height /= 2;
			scale /= 2;
		}

		System.out.println("Loading " + FilenameUtils.getName(path.getAbsolutePath()) + " scaled at " + scale);
		System.out.println("Original size: (" + this.tile.width + "x" + this.tile.height + "), Final size: ("
		    + this.outSize.width + "x" + this.outSize.height + ")");

		// Compute tile size and count
		long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		long ramAvailable = Runtime.getRuntime().maxMemory() - usedMem;
		int processors = Runtime.getRuntime().availableProcessors();

		Dimension loadTileSize = new Dimension(inSize);
		Dimension outTileSize = new Dimension(outSize);
		Dimension loadTileCount = new Dimension(1, 1);

		long loadTileDataSize = (long) channelSize * (long) dataType.getSize() * (long) loadTileSize.width
		    * (long) loadTileSize.height;
		loadTileDataSize += (long) channelSize * (long) dataType.getSize() * (long) outTileSize.width
		    * (long) outTileSize.height;
		long loadOutDataSize = (long) channelSize * (long) dataType.getSize() * (long) outSize.width
		    * (long) outSize.height;
		// System.out.println("Output size: " + loadOutDataSize);
		// System.out.println("Data size: " + loadTileDataSize);
		// System.out.println("Free ram: " + ramAvailable);
		// System.out.println("Free ram after output: " + (ramAvailable -
		// loadOutDataSize));
		if (ramAvailable - loadOutDataSize < 0) {
			throw new OutOfMemoryError("Not enough memory to load image. Size(" + outSize.width + "x" + outSize.height + ")");
		}
		// System.out.println((ramAvailable - loadOutDataSize) / (2*processors) <
		// loadTileDataSize);
		while ((ramAvailable - loadOutDataSize) / (2 * processors) < loadTileDataSize
		    && (loadTileSize.width > 7 || loadTileSize.height > 7) && (outTileSize.width > 1 && outTileSize.height > 1)) {
			if (loadTileSize.width > 7) {
				loadTileSize.width /= 2;
				outTileSize.width /= 2;
				loadTileCount.width *= 2;
			}
			if (loadTileSize.height > 7) {
				loadTileSize.height /= 2;
				outTileSize.height /= 2;
				loadTileCount.height *= 2;
			}

			loadTileDataSize = (long) channelSize * (long) dataType.getSize() * (long) loadTileSize.width
			    * (long) loadTileSize.height;
			loadTileDataSize += (long) channelSize * (long) dataType.getSize() * (long) outTileSize.width
			    * (long) outTileSize.height;
			// System.out.println((long)(ramAvailable - loadOutDataSize) /
			// (long)(2*processors) < (long)loadTileDataSize);
		}
		this.totalProcessedTiles = loadTileCount.width * loadTileCount.height;
		System.out.println("" + totalProcessedTiles + " tiles of " + loadTileSize.width + " by " + loadTileSize.height);
		// Announce loading start
		if (this.listener != null) {
			this.listener.notifyProgress(numProcessedTiles, totalProcessedTiles);
		}

		// Load tiles using a thread pool
		IcyBufferedImage loadedImage = new IcyBufferedImage(outSize.width, outSize.height, channelSize, dataType);
		loadedImage.beginUpdate();

		this.threadPool = Executors.newFixedThreadPool(processors - 1);

		// For each tile in y
		for (int j = 0; j < loadTileCount.height; j++) {
			// set tile height
			int currentTileHeight = (j == loadTileCount.height - 1) ? (inSize.height - j * loadTileSize.height)
			    : loadTileSize.height;
			int currentOutTileHeight = (j == loadTileCount.height - 1) ? (outSize.height - j * outTileSize.height)
			    : outTileSize.height;
			// For each tile in x
			for (int i = 0; i < loadTileCount.width; i++) {
				// set tile width
				int currentTileWidth = (i == loadTileCount.width - 1) ? (inSize.width - i * loadTileSize.width)
				    : loadTileSize.width;
				int currentOutTileWidth = (i == loadTileCount.width - 1) ? (outSize.width - i * outTileSize.width)
				    : outTileSize.width;
				// set tile rectangle
				Dimension currentTileDimension = new Dimension(currentTileWidth, currentTileHeight);
				Point currentTilePosition = new Point(tile.x + i * loadTileSize.width, tile.y + j * loadTileSize.height);
				Rectangle currentTileRectangle = new Rectangle(currentTilePosition, currentTileDimension);

				Dimension currentOutTileDimension = new Dimension(currentOutTileWidth, currentOutTileHeight);
				Point currentOutTilePosition = new Point(i * outTileSize.width, j * outTileSize.height);
				Rectangle currentOutTileRectangle = new Rectangle(currentOutTilePosition, currentOutTileDimension);

				threadPool.submit(new TileReadingTask(path, currentTileRectangle, currentOutTileRectangle, loadedImage));
			}
		}

		threadPool.shutdown();
		try {
			threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		} finally {
			// Array2DUtil.doubleArrayToArray(loadedSequenceData,
			// loadedSequence.getDataXYC(0, 0));
			loadedImage.dataChanged();
			loadedImage.endUpdate();
			loadedSequence = new Sequence(loadedImage);
		}
	}

	/**
	 * @return The loaded sequence.
	 */
	public Sequence getSequence() {
		return loadedSequence;
	}

	/**
	 * Handles the execution interruption
	 */
	public void interrupt() {
		this.isInterrupted = true;
	}

	/**
	 * This class handles the loading of a tile in a big image.
	 * 
	 * @author Daniel Felipe Gonzalez Obando
	 */
	private class TileReadingTask implements Runnable {

		private final File path;
		private final Rectangle tileRectangle;
		private final Rectangle outTileRectangle;
		private final IcyBufferedImage loadedImage;

		/**
		 * @param path
		 * @param tileRectangle
		 * @param outTileRectangle
		 * @param loadedImage
		 */
		public TileReadingTask(final File path, final Rectangle tileRectangle, final Rectangle outTileRectangle,
		    final IcyBufferedImage loadedImage) {
			this.path = path;
			this.tileRectangle = tileRectangle;
			this.outTileRectangle = outTileRectangle;
			this.loadedImage = loadedImage;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {

			long freeMemory = Runtime.getRuntime().maxMemory()
			    - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
			long neededMemory = (long) channelSize * (long) dataType.getSize() * (long) tileRectangle.width
			    * (long) tileRectangle.height;
			neededMemory += (long) channelSize * (long) dataType.getSize() * (long) outTileRectangle.width
			    * (long) outTileRectangle.height;
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
			// System.out.println("Loading " + this.tileRectangle);
			// Open reader
			LociImporterPlugin importer = new LociImporterPlugin();
			try {
				importer.open(path.getAbsolutePath(), 0);

				// Read tile
				IcyBufferedImage resultImage = importer.getImage(0, 0, tileRectangle, 0, 0);
				if (resultImage.getWidth() != outTileRectangle.width || resultImage.getHeight() != outTileRectangle.height) {
					resultImage = IcyBufferedImageUtil.scale(resultImage, outTileRectangle.width, outTileRectangle.height);
				}

				// Copy data to full result
				loadedImage.copyData(resultImage, null, outTileRectangle.getLocation());

				resultImage = null;

			} catch (ClosedByInterruptException e) {
				return;
			} catch (UnsupportedFormatException | IOException e) {
				e.printStackTrace();
			} finally {
				try {
					importer.close();
				} catch (IOException e) {
					e.printStackTrace();
					return;
				} finally {
					synchronized (BigImageReader.this) {
						BigImageReader.this.numProcessedTiles++;
						if (numProcessedTiles % (2 * Runtime.getRuntime().availableProcessors()) == 0) {
							Runtime.getRuntime().gc();
						}
						if (listener != null) {
							BigImageReader.this.listener.notifyProgress(numProcessedTiles, totalProcessedTiles);
						}

						if (isInterrupted && !isShutdownNow) {
							BigImageReader.this.threadPool.shutdownNow();
							BigImageReader.this.isShutdownNow = true;
						}
					}
				}

			}
		}

	}
}
