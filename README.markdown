# Crawla

*Crawla* is a scala based web crawler.

## TODO:

- Allow definition of crawl steps as 
```scala
CrawlStep[T](url:Url)(parseFunc: Document => Seq[T])

val crawler = Crawler(httpClient)

crawl {
  CrawlStep(parseFunc)(url) 
     flatMap { s => CrawlStep(parseFunc2)(url2) } // Now have { source, id, name, long, lat, start, end }
     flatMap { s => ClawlStep(parseFunc3)(url3) } // Submit.php post now have full values { db, siteId, Name, long, lat, time, temp, etc ... } 
}
```