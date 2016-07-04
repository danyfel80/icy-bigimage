package algorithms.danyfel80.bigimage.io;

import java.awt.Rectangle;
import java.io.File;

import org.apache.commons.lang.NotImplementedException;

import icy.common.listener.ProgressListener;
import icy.sequence.Sequence;

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
	final File path;
	/**
	 * The tile to be loaded from the image file.
	 */
	final Rectangle tile;
	/**
	 * The maximum width of the loaded image.
	 */
	final int maxWidth;
	/**
	 * The maximum height of the loaded image.
	 */
	final int maxHeight;
	/**
	 * Listener to manage progress events.
	 */
	ProgressListener listener;
	

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
	 *          Max width of the resulting sequence. If maxWidth is
	 *          0, then the resulting image will have the original size.
	 * @param maxHeight
	 *          Max height of the resulting sequence. If maxHeight is
	 *          0, then the resulting image will have the original size.
	 * @param listener
	 *          Listener to manage progress events.
	 */
	public BigImageReader(final File path, final Rectangle tile, final int maxWidth, final int maxHeight,
	    final ProgressListener listener) {
		this.path = path;
		this.tile = tile;
		this.maxWidth = maxWidth;
		this.maxHeight = maxHeight;
		this.listener = listener;
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		throw new NotImplementedException();
	}

	/**
	 * @return The loaded sequence.
	 */
	public Sequence getSequence() {
		throw new NotImplementedException();
	}
}
