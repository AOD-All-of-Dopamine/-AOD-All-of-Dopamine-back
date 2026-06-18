package com.example.crawler.contents.Novel.NaverSeriesNovel;

import org.jsoup.nodes.Document;

/** Bundle of the two fetches a NaverSeries novel detail needs: the detail page + first-episode date. */
public record NaverSeriesDetail(Document doc, String firstDate) {
}
