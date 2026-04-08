package com.maknoon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

public final class ArabicRootIndexing
{
	private ArabicRootIndexing()
	{
		final ClassLoader cl = ArabicRootIndexing.class.getClassLoader();
		try
		{
			final IndexWriterConfig conf = new IndexWriterConfig(new ArabicRootAnalyzer());
			conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
			final IndexWriter indexWriter = new IndexWriter(FSDirectory.open(new File(cl.getResource("arabicRootsTableIndex").toURI()).toPath()), conf);
			final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("E:/AudioCataloger Media/MS Access DB/test.txt"), StandardCharsets.UTF_8));
			while (in.ready())
			{
				final StringTokenizer tokens = new StringTokenizer(in.readLine(), ",");
				final Document doc = new Document();
				doc.add(new Field("word", tokens.nextToken(), StringField.TYPE_STORED));
				doc.add(new Field("root", tokens.nextToken(), StringField.TYPE_STORED));
				indexWriter.addDocument(doc);
			}
			in.close();
			indexWriter.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void main(String[] arg)
	{
		new ArabicRootIndexing();
	}
}