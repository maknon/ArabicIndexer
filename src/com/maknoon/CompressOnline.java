package com.maknoon;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// This class is to compress online files into multiple zip files so that we can upload it fast and decompress from file manager of the cpanel
// It should be run from the same folder that contains 'bk' since it uses external tar. if from Intellij, change 'Working Directory' before you run it
public class CompressOnline
{
	final String outBase = "F:\\ai-online\\";

	CompressOnline() throws IOException, InterruptedException
	{
		final File folder = new File("./bk/");
		final File[] filesInDir = folder.listFiles();
		String folders = "";
		int count = 0;
		int zipName = 1;
		if (filesInDir != null)
		{
			// No space is allowed in folder names. change them manually (and in menu). very small number
			boolean spaceInName = false;
			for (int i = 0; i < filesInDir.length; i++)
			{
				if(filesInDir[i].getName().contains(" "))
				{
					System.out.println("spaceInName " + filesInDir[i].getName());
					spaceInName = true;
					//if(isEmpty(filesInDir[i]))
						//System.out.println("deleted " + filesInDir[i].delete());
				}
			}

			if(spaceInName)
				return;

			for (int i = 0; i < filesInDir.length; i++)
			{
				folders = folders + " bk\\" + filesInDir[i].getName();

				if (++count == 50 || i == filesInDir.length - 1)
				{
					//final Process proc = Runtime.getRuntime().exec(new String[]{new File("C:/Program Files/WinRAR/winrar.exe").getAbsolutePath(), "a", "-afzip", zipName + ".zip", folders});
					//final Process proc = Runtime.getRuntime().exec("C:/Program Files/WinRAR/winrar.exe a -aftar " + zipName + ".tar" + folders);
					final Process proc = Runtime.getRuntime().exec("tar -cf " + outBase + zipName + ".tar" + folders); // Windows support out of the box with best performance !!

					// Capture the Error/Output streams from the SumatraPDF process
					class StreamGrabber extends Thread
					{
						final InputStream is;
						final String type;

						StreamGrabber(InputStream is, String type)
						{
							this.is = is;
							this.type = type;
						}

						public void run()
						{
							try
							{
								final BufferedReader br = new BufferedReader(new InputStreamReader(is));
								String line;
								while ((line = br.readLine()) != null)
									System.out.println(type + ">" + line);
								br.close();
								is.close();
							}
							catch (IOException ioe)
							{
								ioe.printStackTrace();
							}
						}
					}

					final StreamGrabber errorGrabber = new StreamGrabber(proc.getErrorStream(), "ERROR"); // any error message?
					final StreamGrabber outputGrabber = new StreamGrabber(proc.getInputStream(), "OUTPUT"); // any output?

					errorGrabber.start();
					outputGrabber.start();

					// any error???
					final int exitVal = proc.waitFor();
					System.out.println("ExitValue: " + exitVal);

					if(exitVal == 1)
					{
						System.out.println("ERROR");
						break;
					}
					count = 0;
					zipName++;
					folders = "";
				}
			}
		}

		// Not valid anymore
		//checkfolderFileCount();
	}

	// This function is to check if each folder has equal number of html & png files. some Shamela books has additional pages/attachments more than the PDF. the opposite is been taken care by having empty html files
	void checkfolderFileCount() throws IOException
	{
		final File folder = new File("./bk/");
		final File[] filesInDir = folder.listFiles();
		if (filesInDir != null)
		{
			for (File file : filesInDir)
			{
				final List<File> png = Files.walk(file.toPath()).filter(s -> s.toString().endsWith(".png")).map(Path::toFile).collect(Collectors.toList());
				final List<File> html = Files.walk(file.toPath()).filter(s -> s.toString().endsWith(".html")).map(Path::toFile).collect(Collectors.toList());

				if (png.size() != html.size())
					System.out.println(file.getName());
			}
		}
	}

	public boolean isEmpty(File directory) throws IOException
	{
		DirectoryStream<Path> stream = Files.newDirectoryStream(directory.toPath());
		return !stream.iterator().hasNext();
	}

	public static void main(String[] args) throws IOException, InterruptedException
	{
		new CompressOnline();
	}
}
