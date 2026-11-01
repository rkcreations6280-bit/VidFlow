package sysModules.newPipeLib.parsers;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

import coreUtils.library.process.LoggerUtils;
import sysModules.newPipeLib.cache.YtStreamInfo;

public class SearchUtility {
	private static final LoggerUtils logger = LoggerUtils.from(SearchUtility.class);
	
	public static List<YtStreamInfo> searchVideos(String query, int limit) {
		List<YtStreamInfo> results = new ArrayList<>();
		logger.debug("Searching for: " + query + " with limit: " + limit);
		try {
			SearchExtractor extractor = ServiceList.YouTube.getSearchExtractor(query);
			extractor.fetchPage();
			
			extractor.getInitialPage();
			if (extractor.getInitialPage().getItems() != null) {
				List<InfoItem> items = extractor.getInitialPage().getItems();
				logger.debug("Found " + items.size() + " items for query: " + query);
				for (InfoItem item : items) {
					if (results.size() >= limit) break;
					if (item instanceof StreamInfoItem) {
						results.add(new YtStreamInfo((StreamInfoItem) item));
					}
				}
			} else {
				logger.warning("No results found for query: " + query);
			}
		} catch (Exception error) {
			logger.error("Search failed for query: " + query, error);
		}
		return results;
	}
}
