package algorithms.danyfel80.io.image.big;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.spi.CachingProvider;

import algorithms.danyfel80.io.image.TileProvider;
import icy.common.exception.UnsupportedFormatException;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.type.DataType;

public class BigImageTileImporter implements TileProvider {

	private int tileSizeX = 256;
	private int tileSizeY = 256;

	private int cacheTileSizeX = 1500;
	private int cacheTileSizeY = 1500;
	private int cacheTileLimitX;

	private File file;
	private DataType dataType;
	private int sizeC;
	private BigImageImporter importer;
	private CacheManager cacheManager;
	private Cache<Integer, IcyBufferedImage> cache;

	public BigImageTileImporter(File file) throws IOException, UnsupportedFormatException {
		this.file = file;
		this.importer = new BigImageImporter(this.file, 0, null);

		CachingProvider provider = Caching.getCachingProvider();
		cacheManager = provider.getCacheManager();

		MutableConfiguration<Integer, IcyBufferedImage> configuration = new MutableConfiguration<Integer, IcyBufferedImage>()
				.setTypes(Integer.class, IcyBufferedImage.class).setStoreByValue(false)
				.setExpiryPolicyFactory(EternalExpiryPolicy.factoryOf());
		cache = cacheManager.createCache(file.getName(), configuration);

		Dimension imgDims = BigImageUtil.getImageDimension(file);
		cacheTileLimitX = (imgDims.width + cacheTileSizeX - 1) / cacheTileSizeX;
		dataType = BigImageUtil.getImageDataType(file);
		sizeC = BigImageUtil.getImageChannelCount(file);
	}

	public BigImageTileImporter(File file, int tileSizeX, int tileSizeY) throws IOException, UnsupportedFormatException {
		this(file);
		this.tileSizeX = tileSizeX;
		this.tileSizeY = tileSizeY;
	}

	@Override
	public IcyBufferedImage getTile(Point tile) throws IOException {
		Rectangle tileRegion = new Rectangle(tile.x * tileSizeX, tile.y * tileSizeY, tileSizeX, tileSizeY);
		List<Integer> cacheTiles = getCacheTilesFor(tileRegion);

		IcyBufferedImage tileImage = new IcyBufferedImage(tileSizeX, tileSizeY, sizeC, dataType);
		for (int cacheTileNum : cacheTiles) {
			Rectangle cacheTileRegion = new Rectangle((cacheTileNum % cacheTileLimitX) * cacheTileSizeX,
					(cacheTileNum / cacheTileLimitX) * cacheTileSizeY, cacheTileSizeX, cacheTileSizeY);

			IcyBufferedImage cacheTile = cache.get(cacheTileNum);
			if (cacheTile == null) {
				cacheTile = getCacheTile(cacheTileRegion);
				cache.put(cacheTileNum, cacheTile);
			}

			Point pt = new Point(cacheTileRegion.x - tileRegion.x, cacheTileRegion.y - tileRegion.y);
			tileImage.copyData(cacheTile, null, pt);
		}

		return tileImage;
	}

	private IcyBufferedImage getCacheTile(Rectangle cacheTileRegion) throws IOException {

		importer.setRegionToImport(cacheTileRegion);

		try {
			Sequence tileSeq = importer.call();
			return tileSeq.getFirstImage();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	private List<Integer> getCacheTilesFor(Rectangle tileRegion) {
		int minCacheTileX = (int) tileRegion.getMinX() / cacheTileSizeX;
		int minCacheTileY = (int) tileRegion.getMinY() / cacheTileSizeY;
		int maxCacheTileX = ((int) tileRegion.getMaxX() + cacheTileSizeX - 1) / cacheTileSizeX;
		int maxCacheTileY = ((int) tileRegion.getMaxY() + cacheTileSizeY - 1) / cacheTileSizeY;

		List<Integer> cacheTiles = new LinkedList<>();
		for (int i = minCacheTileX; i < maxCacheTileX; i++) {
			for (int j = minCacheTileY; j < maxCacheTileY; j++) {
				cacheTiles.add(i + j * cacheTileLimitX);
			}
		}
		return cacheTiles;
	}

	@Override
	public void close() throws Exception {
		cacheManager.destroyCache(file.getName());
	}
}
