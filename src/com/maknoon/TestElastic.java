package com.maknoon;

import org.apache.http.HttpHost;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class TestElastic
{
	public static void main(String[] args)
	{
		try
		{
			new TestElastic();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	TestElastic() throws Exception
	{
		final RestHighLevelClient client = new RestHighLevelClient(
				RestClient.builder(
						new HttpHost("localhost", 7543, "http"),
						new HttpHost("localhost", 7543, "http")));

		final GetRequest request = new GetRequest("page", "p-132403-6");
		final GetResponse response = client.get(request, RequestOptions.DEFAULT);
		if (response.isExists())
		{
			String c = response.getSourceAsString()
					.replaceAll("<.*?>", "")
					.replaceAll("\\\\r", "ö");
			final JSONObject obj = new JSONObject(c);
			String str = obj.getString("page");
			str = str.replaceAll("ö", System.lineSeparator());
			System.out.println(str);
		}
		else
			System.out.println("no response");
		client.close();

		if(true)
			return;

		final QueryBuilder matchQueryBuilder = QueryBuilders.boolQuery().must(new QueryStringQueryBuilder("خلدون الأحدب"));

		final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
		//sourceBuilder.query(QueryBuilders.queryStringQuery("*"));
		//sourceBuilder.query(matchQueryBuilder);
		sourceBuilder.query(QueryBuilders.idsQuery().addIds("p-132403-311", "p-132403-312"));
		sourceBuilder.from(0);
		sourceBuilder.size(10);
		//sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));

		final SearchRequest searchRequest = new SearchRequest("page");
		searchRequest.source(sourceBuilder);

		final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(new File("C:/Users/Ebrahim/Desktop/", "el.csv")), StandardCharsets.UTF_8);
		out.write('\ufeff'); // BOM for excel to open it
		final SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
		SearchHit[] searchHits = searchResponse.getHits().getHits();
		for (SearchHit hit : searchHits)
		{
			// Do something with the SearchHit
			String s = hit.getSourceAsString();
			final JSONObject obj = new JSONObject(s);
			out.write(hit.getId() + "," + obj.get("page").toString().replaceAll("\\r\\n|\\r|\\n", " ") + System.lineSeparator());
		}
		out.close();
		client.close();
	}
}
