/**
 * 
 */
package algorithms.danyfel80.io.image.tileprovider;

import java.awt.Point;
import java.io.IOException;

import algorithms.danyfel80.io.image.TileProvider;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;

/**
 * 
 * @author daniel
 */
public class IcyBufferedImageTileProvider implements TileProvider {

	private int tileSizeX = 256, tileSizeY = 256;
	private IcyBufferedImage image;

	public IcyBufferedImageTileProvider(IcyBufferedImage image) {
		this.image = image;
	}

	@Override
	public void close() throws Exception {
		// Nothing to do here.
	}

	@Override
	public IcyBufferedImage getTile(Point tile) throws IOException {
		int x = tile.x * tileSizeX, y = tile.y * tileSizeY;
		int w = (x + tileSizeX < image.getWidth() ? tileSizeX : image.getWidth() - x),
				h = (y + tileSizeY < image.getHeight() ? tileSizeY : image.getHeight() - y);
		IcyBufferedImage tileImage = IcyBufferedImageUtil.getSubImage(image, x, y, w, h);
		return tileImage;
	}

}
