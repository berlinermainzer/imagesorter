package de.felten.imagesorter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

public class ImageSorter {

	private static Logger logger = LoggerFactory.getLogger(ImageSorter.class);
	
	private static Pattern datePattern1 = Pattern.compile("(\\d{2})\\.(\\d{2})\\.(\\d{2})"); // 19.06.15
	private static Pattern datePattern2 = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})"); // 2011-10-07
	private static Pattern datePattern3 = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})"); // 2015-03-22-23
	private static Pattern datePattern4 = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2}) #\\d+"); // 2015-07-15 #2
	private static Pattern datePattern5 = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2}) - \\d{2}-\\d{2}"); // 2015-11-30 - 12-01
	private static Pattern datePattern7 = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})_\\d{2}.\\d{2}.\\d{2}"); // 2015-07-09_22.55.55
	private static Pattern datePattern8 = Pattern.compile("IMG_(\\d{4})(\\d{2})(\\d{2})_\\d{6}"); // IMG_20140125_133117.jpg
	private static Pattern datePattern9 = Pattern.compile("IMG_(\\d{4})(\\d{2})(\\d{2})_\\d{6}_\\d"); // IMG_20140125_133117_1.jpg
	private static Pattern datePattern10 = Pattern.compile("(\\d{2})\\.(\\d{2})\\.(\\d{2}) - \\d"); // 25.05.13 - 1
	private static Pattern datePattern11 = Pattern.compile("IMG-(\\d{4})(\\d{2})(\\d{2})-WA\\d{4}"); // IMG-20140107-WA0001
	private static Pattern datePattern12 = Pattern.compile("(\\d{2})\\.(\\d{2})\\.(\\d{2})\\s+"); // 25.05.13 .jpg
	private static Pattern datePattern13 = Pattern.compile("(\\d{2})-(\\d{2})-(\\d{2})_\\d{4}"); // 14-01-09_1427.jpg

	private static Pattern datePattern6 = Pattern.compile("VID_(\\d{4})(\\d{2})(\\d{2}).*"); // 2011-10-07

	static Path outPathRoot;

	public ImageSorter() throws IOException {
	}

	public static void main(String[] args) throws ImageProcessingException, IOException {
		logger.info("Starte.");
		String rootPath = "C:/Users/felten/Documents/privat/Bilder/Handy_Johannes";
		String outPath = "C:/Users/felten/Documents/privat/Bilder/__sortiert";
//		String rootPath = "C:/Users/felten/Documents/privat/JavaProjects/ImageSorter/tests/in";
//		String outPath = "C:/Users/felten/Documents/privat/JavaProjects/ImageSorter/tests/out";
		logger.info("Scanne {}, sortiere nach {}.", rootPath, outPath);
		outPathRoot = Files.createDirectories(Paths.get(outPath));

		Stream<Path> jpgFiles = Files.walk(new File(rootPath).toPath())
				.filter(p -> p.toString().toLowerCase().endsWith(".jpg") || p.toString().endsWith(".jpeg"));

		jpgFiles.forEach(ImageSorter::processJPGs);

		// outPath = "C:/Users/felten/Documents/privat/Videos/sorted/";
		// outPathRoot = Files.createDirectories(Paths.get(outPath));
		//
		// Stream<Path> mp4Files = Files.walk(new File(rootPath).toPath())
		// .filter(p -> p.toString().toLowerCase()
		// .endsWith(".mp4"));
		// mp4Files.forEach(ImageSorter::processMP4s);
		logger.info("Fertig.");
	}

	private static void processJPGs(Path file) {
		logger.info("Verarbeite " + file);

		File jpegFile = file.toFile();
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(jpegFile);
			// obtain the Exif directory
			ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
			if (directory != null) {
				Date date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
				if (date == null) {
					logger.info("Aufgenommen am: N/A");
					// Koennte Google-Datumsstruktur sein, in der das
					// Verzeichnis ein Datum beschreibt
					String dateStr = jpegFile.getParentFile().getAbsoluteFile().getName();
					logger.info("\tVersuche Google-Datumsstruktur");
					if(processDirectoryBased(file, dateStr)) {
						return;
					}
					
					logger.info("\tVersuche namensbasiert");
					dateStr = stripExtension(jpegFile.getAbsoluteFile().getName());
					if (!processDirectoryBased(file, dateStr)) {
						logger.error("Huh! Konnte {} nicht verarbeiten.", jpegFile);
					}
				} else {
					LocalDate ld = Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
					logger.info("Aufgenommen am: " + ld.toString());

					String yearString = String.format("%04d", ld.getYear());
					String monthString = String.format("%02d", ld.getMonthValue());
					Path outDir = Files
							.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
					moveAndRenameIfNecessary(file, outDir);
				}
			} else {
				logger.info("Keine Metadaten vorhanden.");
				// Koennte Google-Datumsstruktur sein, in der das Verzeichnis
				// ein Datum beschreibt
				String dateStr = jpegFile.getParentFile().getAbsoluteFile().getName();
				logger.info("Versuche Google-Datumsstruktur");
				if (processDirectoryBased(file, dateStr)) {
					return;
				}
				dateStr = stripExtension(jpegFile.getAbsoluteFile().getName());
				logger.info("Versuche namensbasiert");
				if (!processDirectoryBased(file, dateStr)) {
					logger.error("Hah! Konnte {} nicht verarbeiten.", jpegFile);
				}
			}
		} catch (ImageProcessingException | IOException e) {
			e.printStackTrace();
		}

	}

	private static boolean moveFilenameBased(Path file, String dateStr) throws IOException {
		final Matcher matcher1 = datePattern6.matcher(dateStr);
		if (matcher1.matches()) {
			String yearString = matcher1.group(1);
			String monthString = matcher1.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}
		return false;
	}

	/**
	 * @param file
	 * @param dateStr
	 * @throws IOException
	 */
	private static boolean processDirectoryBased(Path file, String dateStr) throws IOException {
		final Matcher matcher1 = datePattern1.matcher(dateStr);
		if (matcher1.matches()) {
			logger.info("Matcher 1");
			String yearString = "20" + matcher1.group(3);
			String monthString = matcher1.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}

		final Matcher matcher2 = datePattern2.matcher(dateStr);
		if (matcher2.matches()) {
			logger.info("Matcher 2");
			String yearString = matcher2.group(1);
			String monthString = matcher2.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}

		final Matcher matcher3 = datePattern3.matcher(dateStr);
		if (matcher3.matches()) {
			logger.info("Matcher 3");
			String yearString = matcher3.group(1);
			String monthString = matcher3.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}

		final Matcher matcher4 = datePattern4.matcher(dateStr);
		if (matcher4.matches()) {
			logger.info("Matcher 4");
			String yearString = matcher4.group(1);
			String monthString = matcher4.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}

		final Matcher matcher5 = datePattern5.matcher(dateStr);
		if (matcher5.matches()) {
			logger.info("Matcher 5");
			String yearString = matcher5.group(1);
			String monthString = matcher5.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}

		final Matcher matcher7 = datePattern7.matcher(dateStr);
		if (matcher7.matches()) {
			logger.info("Matcher 7");
			String yearString = matcher7.group(1);
			String monthString = matcher7.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}

		final Matcher matcher8 = datePattern8.matcher(dateStr);
		if (matcher8.matches()) {
			logger.info("Matcher 8");
			String yearString = matcher8.group(1);
			String monthString = matcher8.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}

		final Matcher matcher9 = datePattern9.matcher(dateStr);
		if (matcher9.matches()) {
			logger.info("Matcher 9");
			String yearString = matcher9.group(1);
			String monthString = matcher9.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}

		final Matcher matcher10 = datePattern10.matcher(dateStr);
		if (matcher10.matches()) {
			logger.info("Matcher 10");
			String yearString = "20" + matcher10.group(3);
			String monthString = matcher10.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}

		final Matcher matcher11 = datePattern11.matcher(dateStr);
		if (matcher11.matches()) {
			logger.info("Matcher 11");
			String yearString = matcher11.group(1);
			String monthString = matcher11.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}
		
		final Matcher matcher12 = datePattern12.matcher(dateStr);
		if (matcher12.matches()) {
			logger.info("Matcher 12");
			String yearString = "20" + matcher12.group(3);
			String monthString = matcher12.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}
		
		final Matcher matcher13 = datePattern13.matcher(dateStr);
		if (matcher13.matches()) {
			logger.info("Matcher 13");
			String yearString = "20" + matcher13.group(3);
			String monthString = matcher13.group(2);
			Path outDir = Files.createDirectories(Paths.get(outPathRoot + "/" + yearString + "/" + monthString));
			moveAndRenameIfNecessary(file, outDir);
			return true;
		}

		logger.warn("Kein Matcher gefunden.");
		return false;

	}

	/**
	 * @param file
	 * @param outDir
	 * @throws IOException
	 */
	private static void moveAndRenameIfNecessary(Path file, Path outDir) {
		try {
			Path destPath = getUniqueFilepath(file, outDir);
			Files.move(file.toAbsolutePath(), destPath.toAbsolutePath());
			logger.info("Verschoben nach " + destPath);
		} catch (IOException e) {
			logger.error("Konnte " + file + " nicht nach " + outDir + " verschieben: ", e);
		}
	}

	/**
	 * Kopiert Datei srcFile ins Verzeichnis destinationDir. Haengt ggf. _ an
	 * den Dateinamne, falls Datei bereits exisitert.
	 * 
	 * @param srcFile
	 * @param destinationDir
	 */
	private static void copyAndRenameIfNecessary(Path srcFile, Path destinationDir) {
		try {
			Path destPath = getUniqueFilepath(srcFile, destinationDir);
			Files.copy(srcFile.toAbsolutePath(), destPath.toAbsolutePath());
			logger.info("Kopiere " + srcFile + " nach " + destPath + ".");
		} catch (IOException e) {
			logger.error("Konnte " + srcFile + " nicht nach " + destinationDir + " kopieren: ", e);
		}
	}

	/**
	 * Erzeugt eindeutigen Dateipfad. Haengt ggf. _ an den Dateinamne, falls
	 * srcFile bereits exisitert
	 * 
	 * @param srcFile
	 * @param destinationDir
	 * @return
	 */
	private static Path getUniqueFilepath(Path srcFile, Path destinationDir) {
		Path destPath = destinationDir.resolve(srcFile.getFileName());
		while (Files.exists(destPath)) {
			String fileName = destPath.getFileName().toString();
			int dotIdx = fileName.lastIndexOf('.');
			String beforeDot = fileName.substring(0, dotIdx);
			String suffix = fileName.substring(dotIdx);
			destPath = destinationDir.resolve(beforeDot + "_" + suffix);
		}
		return destPath;
	}

	private static void processMP4s(Path file) {
		logger.info("Verarbeite " + file);

		try {
			File jpegFile = file.toFile();
			// Koennte Google-Datumsstruktur sein, in der das Verzeichnis ein
			// Datum beschreibt
			String dateStr = jpegFile.getParentFile().getAbsoluteFile().getName();
			if (processDirectoryBased(file, dateStr)) {
				return;
			}
			if (moveFilenameBased(file, dateStr)) {
				return;
			}
		} catch (IOException e) {
			logger.error("DOh!", e);
		}

	}

	static String stripExtension(String str) {
		if (str == null) {
			return null;
		}

		int pos = str.lastIndexOf(".");

		if (pos == -1) {
			return str;
		}

		return str.substring(0, pos);
	}
}
