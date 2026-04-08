package com.maknoon;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// This class is to move the duplicate files from biuf-v3 that is matched with biuf-v4. only the files that has the same name and content
// the duplicate list is coming from external app (WinMerge) to compare the pdf folders of 2 AI versions (v3/v4)
public class MoveShamelaV4Duplicates
{
	MoveShamelaV4Duplicates() throws IOException
	{
		final String v3 = "F:/biuf-v3";
		final String v4 = "F:/biuf-v4";
		final String duplicateFiles = "F:/biuf-v3-duplicates-with-v4/duplicate.txt";
		final String v3_duplicates = "F:/biuf-v3-duplicates-with-v4";

		final List<File> files = Files.walk(Paths.get(v3)).filter(s -> s.toString().endsWith(".biuf")).map(Path::toFile).collect(Collectors.toList());
		final Vector<ZipData> buif = new Vector<>();
		for (File f : files)
		{
			final ZipFile zipFile = new ZipFile(f);
			boolean zipWithPdf = false;
			for (Enumeration<? extends ZipEntry> en = zipFile.entries(); en.hasMoreElements(); )
			{
				final ZipEntry zipEntry = en.nextElement();
				if (zipEntry.getName().toLowerCase().endsWith(".pdf"))
				{
					buif.add(new ZipData(f, zipEntry.getName().toLowerCase()));
					zipWithPdf = true;
				}
			}
			zipFile.close();

			if(!zipWithPdf)
			{
				System.out.println("Zip File without PDF ! " + f);
				return;
			}
		}

		final List<String> extractedLine = Files.readAllLines(Paths.get(duplicateFiles));
		for (String f : extractedLine)
		{
			for (ZipData z : buif)
			{
				if(z.v3ZipFileName.equals(f))
				{
					System.out.println("File " + z.v3);
					try
					{
						Files.move(z.v3.toPath(), Paths.get(v3_duplicates + "/" + z.v3.getName()), StandardCopyOption.REPLACE_EXISTING);
					}
					catch (NoSuchFileException e)
					{
						e.printStackTrace();
					}
				}
			}
		}
	}

	public static void main(String[] args) throws IOException
	{
		new MoveShamelaV4Duplicates();
	}

	class ZipData
	{
		File v3;
		String v3ZipFileName;

		ZipData(File v3, String v3ZipFileName)
		{
			this.v3 = v3;
			this.v3ZipFileName = v3ZipFileName;
		}
	}
}
