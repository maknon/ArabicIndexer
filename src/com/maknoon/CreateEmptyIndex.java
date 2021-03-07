package com.maknoon;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import java.io.File;

public final class CreateEmptyIndex
{
	private CreateEmptyIndex()
	{
		try
		{
			final String programFolder = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath() + "/";
			final IndexWriterConfig conf = new IndexWriterConfig(new StandardAnalyzer());
			conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
			final IndexWriter indexWriter = new IndexWriter(FSDirectory.open(new File(programFolder + "arabicIndex").toPath()), conf);
			indexWriter.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void main(String[] args)
	{
		new CreateEmptyIndex();
	}
}