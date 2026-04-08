package com.maknoon;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// This class is to display the duplicate pdf files from biuf-v4 to prepare it for OnlineConverter.
// It is not allowed for Online version to have multiple biuf that has duplicate pdf names (name + size)
public class ShamelaV4Duplicates
{
	ShamelaV4Duplicates() throws IOException
	{
		final String v4 = "F:/biuf-v4";

		final List<File> files = Files.walk(Paths.get(v4)).filter(s -> s.toString().endsWith(".biuf")).map(Path::toFile).collect(Collectors.toList());
		final Vector<String> buif = new Vector<>();
		final Vector<String> zipFileName = new Vector<>();
		final Vector<Long> size = new Vector<>();
		for (File f : files)
		{
			final ZipFile zipFile = new ZipFile(f);
			boolean zipWithPdf = false;
			for (Enumeration<? extends ZipEntry> en = zipFile.entries(); en.hasMoreElements(); )
			{
				final ZipEntry zipEntry = en.nextElement();
				if (zipEntry.getName().toLowerCase().endsWith(".pdf"))
				{
					buif.add(f.toString());
					zipFileName.add(zipEntry.getName().toLowerCase());
					size.add(zipEntry.getSize());
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

		for(int i=0; i<buif.size(); i++)
		{
			List<Integer> a = indexOfAll(zipFileName.elementAt(i), zipFileName);
			for(int j=0; j<a.size(); j++)
			{
				int p = a.get(j);
				if(p != i && !buif.elementAt(i).equals(buif.elementAt(p)) && size.elementAt(i).equals(size.elementAt(p)))
				{
					System.out.println(buif.elementAt(i));
					System.out.println(buif.elementAt(p));
					System.out.println("****************");
				}
			}
		}
	}

	static <T> List<Integer> indexOfAll(T obj, List<T> list) {
		final List<Integer> indexList = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			if (obj.equals(list.get(i))) {
				indexList.add(i);
			}
		}
		return indexList;
	}

	public static void main(String[] args) throws IOException
	{
		new ShamelaV4Duplicates();
	}
}
