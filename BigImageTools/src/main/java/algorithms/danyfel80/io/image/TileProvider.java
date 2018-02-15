package algorithms.danyfel80.io.image;

import java.awt.Point;
import java.io.IOException;

import icy.image.IcyBufferedImage;

/**
 * Classes implementing this interfaces can provide tiles of an image as
 * requested by {@link #getTile(Point)} method.
 * 
 * @author Daniel Felipe Gonzalez Obando
 *
 */
public interface TileProvider extends AutoCloseable {

	/**
	 * @param tile
	 *          Tile to be returned.
	 * @return An image of the specified tile.
	 * @throws IOException
	 *           If the tile cannot be retrieved.
	 */
	IcyBufferedImage getTile(Point tile) throws IOException;
}
