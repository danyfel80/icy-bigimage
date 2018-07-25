/**
 * 
 */
package algorithms.danyfel80.io.sequence.tileprovider;

import java.awt.Dimension;
import java.awt.Point;
import java.io.IOException;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;

/**
 * 
 * @author daniel
 */
public class IcyBufferedImageTileProvider implements ITileProvider {

	private IcyBufferedImage image;
	private Dimension tileSize;

	public IcyBufferedImageTileProvider(IcyBufferedImage image) {
		this.image = image;
		this.tileSize = new Dimension(256, 256);
	}
	
	public void setTileSize(Dimension tileSize) {
		this.tileSize.setSize(tileSize);
	}

	@Override
	public IcyBufferedImage getTile(Point tile) throws IOException {
		int x = tile.x * tileSize.width, y = tile.y * tileSize.height;
		int w = (x + tileSize.width < image.getWidth() ? tileSize.width : image.getWidth() - x),
				h = (y + tileSize.height < image.getHeight() ? tileSize.height : image.getHeight() - y);
		IcyBufferedImage tileImage = IcyBufferedImageUtil.getSubImage(image, x, y, w, h);
		return tileImage;
	}

}
