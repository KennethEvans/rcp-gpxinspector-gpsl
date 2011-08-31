package net.kenevans.gpxinspector.gpsl.converters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import net.kenevans.core.utils.SWTUtils;
import net.kenevans.core.utils.Utils;
import net.kenevans.gpx.GpxType;
import net.kenevans.gpx.RteType;
import net.kenevans.gpx.TrkType;
import net.kenevans.gpx.TrksegType;
import net.kenevans.gpx.WptType;
import net.kenevans.gpxinspector.converters.IGpxConverter;
import net.kenevans.parser.GPXParser;

/*
 * Created on May 12, 2011
 * By Kenneth Evans, Jr.
 */

public class GpslConverter implements IGpxConverter
{
    private static final String[] extensions = {".gpsl"};
    private static final String GARMINTIME0 = "GarminTime0";
    private static final String GPSLINK_ID = "!GPSLINK";
    private static final String DELIMITER = "Delimiter";
    private static final String GMTOFFSET = "GMTOffset";
    private static final double M2FT = 3.280839895;

    /*
     * (non-Javadoc)
     * 
     * @see
     * net.kenevans.gpxinspector.converters.IGpxConverter#getFilterExtensions()
     */
    @Override
    public String getFilterExtensions() {
        String retVal = "";
        for(String ext : extensions) {
            if(retVal.length() > 0) {
                retVal += ";";
            }
            retVal += "*" + ext;
        }
        return retVal;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * net.kenevans.gpxinspector.converters.IGpxConverter#isReadSupported(java
     * .lang.String)
     */
    @Override
    public boolean isParseSupported(File file) {
        String fileExt = "." + Utils.getExtension(file);
        if(fileExt != null) {
            for(String ext : extensions) {
                if(fileExt.equalsIgnoreCase(ext)) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * net.kenevans.gpxinspector.converters.IGpxConverter#isWriteSupported(java
     * .lang.String)
     */
    @Override
    public boolean isSaveSupported(File file) {
        String fileExt = "." + Utils.getExtension(file);
        if(fileExt != null) {
            for(String ext : extensions) {
                if(fileExt.equalsIgnoreCase(ext)) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * net.kenevans.gpxinspector.converters.IGpxConverter#parse(java.io.File)
     */
    @Override
    public GpxType parse(File file) throws Throwable {
        // The radius in miles
        long lineNum = 0;
        boolean error = false;
        String line = null;
        String[] tokens = null;
        String delimiter = "\t";
        String name = null;
        String offset;
        // double fileGMTOffsetHr = 0.0;
        boolean rteDataInProgress = false;
        boolean trkDataInProgress = false;
        RteType rteType = null;
        TrkType trkType = null;
        WptType wptType = null;
        TrksegType trksegType = null;
        // Use strings here as they convert to BigDecimal better
        String latitude;
        String longitude;
        String altitude;
        String symbol = null;
        String time;
        // String time = "";
        boolean startSegment = false;

        GpxType gpx = new GpxType();
        // These will be overwritten when saving as .gpx
        gpx.setCreator("GPSL Converter for GPX Inspector");
        GPXParser.setMetaDataTime(gpx);

        BufferedReader in = null;
        in = new BufferedReader(new FileReader(file));

        // Read ID
        line = in.readLine();
        lineNum++;
        if(line == null) {
            SWTUtils.errMsg("Unexpected end of file at line " + lineNum + ":\n"
                + file.getName());
            return null;
        }
        if(!line.equals(GPSLINK_ID)) {
            SWTUtils.errMsg("Invalid GPSLink file (Bad ID) at line " + lineNum
                + ":\n" + file.getName());
            return null;
        }

        // Read timestamp
        line = in.readLine();
        lineNum++;
        if(line == null) {
            SWTUtils.errMsg("Unexpected end of file at line " + lineNum + ":\n"
                + file.getName());
            return null;
        }

        // Delimiter
        line = in.readLine();
        lineNum++;
        if(line == null) {
            SWTUtils.errMsg("Unexpected end of file at line " + lineNum + ":\n"
                + file.getName());
            return null;
        }
        tokens = line.split("=");
        if(tokens.length < 2 || !tokens[0].equals(DELIMITER)) {
            SWTUtils.warnMsg("No delimiter found at line + lineNum "
                + ", assuming TAB:\n" + file.getName());
            delimiter = "\t";
        }
        delimiter = tokens[1];
        if(!delimiter.equals(",") && !delimiter.equals("\t")) {
            SWTUtils.warnMsg("Invalid delimiter found at line + lineNum "
                + ", assuming TAB:\n" + file.getName());
            delimiter = "\t";
        }

        // GMTOffset
        line = in.readLine();
        lineNum++;
        if(line == null) {
            SWTUtils.errMsg("Unexpected end of file at line " + lineNum + ":\n"
                + file.getName());
            return null;
        }
        tokens = line.split("=");
        if(tokens.length < 2 || !tokens[0].equals(GMTOFFSET)) {
            SWTUtils.warnMsg("No " + GMTOFFSET + " found at " + lineNum
                + ", assuming 0:\n" + file.getName());
            offset = "0";
        } else {
            offset = tokens[1];
        }

        // Loop over the rest of the lines
        while((line = in.readLine()) != null) {
            lineNum++;

            // A blank line will terminate routes and tracks in progress
            if(line.length() == 0) {
                if(rteDataInProgress) {
                    rteDataInProgress = false;
                    rteType = null;
                }
                if(trkDataInProgress) {
                    trkDataInProgress = false;
                    trkType = null;
                    trksegType = null;
                }
            }
            // Skip comments
            if(line.startsWith("#")) continue;
            // Insure there at least two characters
            if(line.length() < 2) continue;
            // Only handle lines that have a type identifier
            if(!line.subSequence(1, 2).equals(delimiter)) continue;

            // Branch on type
            tokens = line.split(delimiter);
            String startChar = line.substring(0, 1);
            if(startChar.equals("W")) {
                // Waypoint
                name = tokens[1];
                latitude = tokens[2];
                longitude = tokens[3];
                altitude = String.format("%.6f", Double.valueOf(tokens[4])
                    .doubleValue() / M2FT);
                symbol = tokens[5];
                wptType = new WptType();
                if(wptType == null) {
                    SWTUtils.errMsg("Line " + lineNum
                        + ": Cannot create route waypoint:\n" + file.getName());
                    error = true;
                    break;
                }
                wptType.setName(name);
                wptType.setDesc(name);
                wptType.setLat(new BigDecimal(latitude));
                wptType.setLon(new BigDecimal(longitude));
                wptType.setEle(new BigDecimal(altitude));
                wptType.setSym(symbol);
                if(rteDataInProgress) {
                    // Route waypoint
                    if(rteType != null) {
                        rteType.getRtept().add(wptType);
                    }
                } else {
                    // Regular waypoint
                    // TODO Handle duplicates ?
                    gpx.getWpt().add(wptType);
                }
            } else if(startChar.equals("R")) {
                // Route
                name = tokens[1];
                rteType = new RteType();
                if(rteType == null) {
                    SWTUtils.errMsg("Line " + lineNum
                        + ": Cannot create route:\n" + file.getName());
                    error = true;
                    break;
                }
                rteType.setName(name);
                gpx.getRte().add(rteType);
                rteDataInProgress = true;
            } else if(startChar.equals("H")) {
                // Track
                name = tokens[1];
                if(name == null) {
                    SWTUtils.errMsg("Line " + lineNum
                        + " Cannot create track:\n" + file.getName());
                    error = true;
                    break;
                }
                trkType = new TrkType();
                trkType.setName(name);
                trkType.setDesc(name);
                if(trkType == null) {
                    SWTUtils.errMsg("Line " + lineNum
                        + ": Cannot create track:\n" + file.getName());
                    error = true;
                    break;
                }
                gpx.getTrk().add(trkType);
                trkDataInProgress = true;
            } else if(startChar.equals("T")) {
                // TrackPoint
                if(tokens.length < 6) {
                    SWTUtils.errMsg("Line " + lineNum
                        + ": invalid trackpoint:\n" + file.getName());
                    error = true;
                    break;
                }
                name = tokens[1];
                latitude = tokens[2];
                longitude = tokens[3];
                altitude = String.format("%.6f", Double.valueOf(tokens[4])
                    .doubleValue() / M2FT);
                time = tokens[5];
                symbol = "";
                wptType = new WptType();
                if(wptType == null) {
                    SWTUtils.errMsg("Line " + lineNum
                        + ": Cannot create route waypoint:\n" + file.getName());
                    error = true;
                    break;
                }
                wptType.setLat(new BigDecimal(latitude));
                wptType.setLon(new BigDecimal(longitude));
                wptType.setEle(new BigDecimal(altitude));
                XMLGregorianCalendar xgcal = getXMLGregorianCalendarFromTimeStamp(
                    time, offset);
                if(xgcal != null) {
                    wptType.setTime(xgcal);
                }
                // Decide if it a new track based on the first character
                if(name != null && name.substring(0, 1).equalsIgnoreCase("C")) {
                    startSegment = false;
                } else {
                    startSegment = true;
                }
                if(trkDataInProgress) {
                    if(trkType != null) {
                        if(startSegment) {
                            trksegType = new TrksegType();
                            if(trksegType == null) {
                                SWTUtils.errMsg("Line " + lineNum
                                    + ": Cannot create track segment:\n"
                                    + file.getName());
                                error = true;
                                break;
                            }
                            trkType.getTrkseg().add(trksegType);
                        }
                    } else {
                        SWTUtils.errMsg("Line " + lineNum
                            + " Found trackpoint without track:\n"
                            + file.getName());
                    }
                    trksegType.getTrkpt().add(wptType);
                }
            }
        }
        if(in != null) in.close();
        if(error) return null;
        return gpx;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * net.kenevans.gpxinspector.converters.IGpxConverter#save(net.kenevans.
     * gpx.GpxType, java.io.File)
     */
    @Override
    public void save(String creator, GpxType gpxType, File file)
        throws Throwable {
        // Use this to avoid the possibility of mixed CF and CRLF
        final String ls = SWTUtils.LS;
        final String delimiter = "\t";
        String timeStamp;
        double offset = 0;
        String name;
        double latitude;
        double longitude;
        double altitude;
        String symbol;
        String time;
        GregorianCalendar gcal;
        XMLGregorianCalendar xgcal;

        // Assume the file is not null and any asking to overwrite has been done
        // already
        file.createNewFile();
        BufferedReader in = null;
        PrintWriter out = null;
        try {
            in = new BufferedReader(new FileReader(file));
            out = new PrintWriter(new FileWriter(file));
            // Get the offset to use. Only the GMT time is stored in the
            // GpxType. We don't know the offset at the time the file was made.
            // Further, we do not know the time zone. Each time time zone could
            // have two offsets, depending on whether it is DST or not. There is
            // the further complication that the file could have tracks made
            // both under DST and not. (That one we cannot handle.) The
            // procedure used is to find the time of the first trackpoint in the
            // file, determine its offset in the current time zone, and use
            // that. If there is no trackpoint, use the offset for the current
            // time in the current time zone. Thus will tend to work as we want
            // (have the time in the GPSL file be the clock time at the time of
            // the tracks) if the user stays in one time zone and does not mix
            // tracks with and without DST.
            xgcal = null;
            try {
                // Try to find the first trackpoint
                if(gpxType.getTrk().size() > 0) {
                    for(TrkType trk : gpxType.getTrk()) {
                        for(TrksegType seg : trk.getTrkseg()) {
                            for(WptType wpt : seg.getTrkpt()) {
                                xgcal = wpt.getTime();
                                if(xgcal == null) {
                                    continue;
                                }
                            }
                        }
                    }
                }
                if(xgcal != null) {
                    // Use the time from the first trackpoint
                    gcal = xgcal.toGregorianCalendar();
                    // Get the date
                    Date date = gcal.getTime();
                    // Make a new local GregorianCalendar with this date
                    gcal = new GregorianCalendar();
                    gcal.setTime(date);
                } else {
                    // Make a new local GregorianCalendar with the current date
                    gcal = new GregorianCalendar();
                    gcal.setTime(new Date());
                }
                // Make a new local XMLGregorianCalendar
                xgcal = DatatypeFactory.newInstance().newXMLGregorianCalendar(
                    gcal);
                // Get its offset
                offset = xgcal.getTimezone() / 60.;
            } catch(Throwable t) {
                // Do nothing, use 0
            }

            // Print header
            out.print(GPSLINK_ID + ls);
            timeStamp = Utils.timeStamp("MMM dd, yyyy hh:mm:ssa");
            // Convert AM/PM
            if(timeStamp.substring(21, 22).equalsIgnoreCase("P")) {
                timeStamp = timeStamp.substring(0, 21) + "p";
            } else {
                timeStamp = timeStamp.substring(0, 21) + "a";
            }
            out.print("Saved " + timeStamp + ls);
            out.print("Delimiter=" + delimiter + ls);
            // This prints e.g. -5.0 instead of -5, but leave it
            out.print("GMTOffset=" + offset + ls);

            // Waypoints
            if(gpxType.getWpt().size() > 0) {
                out.print(ls + "Waypoints" + ls);
                out.print(String.format("%s%s%s%s%s%s%s%s%s%s%s", "Type",
                    delimiter, "Name", delimiter, "Latitude", delimiter,
                    "Longitude", delimiter, "Alt", delimiter, "Symbol")
                    + ls);
                for(WptType wpt : gpxType.getWpt()) {
                    name = wpt.getName();
                    latitude = wpt.getLat().doubleValue();
                    longitude = wpt.getLon().doubleValue();
                    altitude = wpt.getEle().doubleValue() * M2FT;
                    symbol = wpt.getSym();
                    out.print(String.format("W%s%s%s%.6f%s%.6f%s%.0f%s%s",
                        delimiter, name, delimiter, latitude, delimiter,
                        longitude, delimiter, altitude, delimiter, symbol)
                        + ls);
                }
            }

            // Routes
            if(gpxType.getRte().size() > 0) {
                out.print(ls + "Routes" + ls);
                out.print(String.format("%s%s%s%s%s%s%s%s%s%s%s", "Type",
                    delimiter, "Name", delimiter, "Latitude", delimiter,
                    "Longitude", delimiter, "Alt", delimiter, "Symbol")
                    + ls);
                for(RteType rte : gpxType.getRte()) {
                    name = rte.getName();
                    out.print(String.format("R%s%s", delimiter, name) + ls);
                    for(WptType wpt : rte.getRtept()) {
                        name = wpt.getName();
                        latitude = wpt.getLat().doubleValue();
                        longitude = wpt.getLon().doubleValue();
                        altitude = wpt.getEle().doubleValue() * M2FT;
                        symbol = wpt.getSym();
                        out.print(String.format("W%s%s%s%.6f%s%.6f%s%.0f%s%s",
                            delimiter, name, delimiter, latitude, delimiter,
                            longitude, delimiter, altitude, delimiter, symbol)
                            + ls);
                    }
                }
            }

            // Tracks
            boolean first;
            if(gpxType.getTrk().size() > 0) {
                out.print(ls + "Tracks" + ls);
                out.print(String.format("%s%s%s%s%s%s%s%s%s%s%s", "Type",
                    delimiter, "Name", delimiter, "Latitude", delimiter,
                    "Longitude", delimiter, "Alt", delimiter, "Time")
                    + ls);
                for(TrkType trk : gpxType.getTrk()) {
                    name = trk.getName();
                    out.print(String.format("H%s%s", delimiter, name) + ls);
                    for(TrksegType seg : trk.getTrkseg()) {
                        first = true;
                        for(WptType wpt : seg.getTrkpt()) {
                            if(first) {
                                first = false;
                                name = "Start";
                            } else {
                                name = "Cont";
                            }
                            latitude = wpt.getLat().doubleValue();
                            longitude = wpt.getLon().doubleValue();
                            altitude = wpt.getEle().doubleValue() * M2FT;
                            symbol = wpt.getSym();
                            xgcal = wpt.getTime();
                            if(xgcal == null) {
                                time = GARMINTIME0;
                            } else {
                                time = getTimeFromXMLGregorianCalendar(xgcal,
                                    offset);
                            }
                            out.print(String.format(
                                "T%s%s%s%.6f%s%.6f%s%.0f%s%s", delimiter, name,
                                delimiter, latitude, delimiter, longitude,
                                delimiter, altitude, delimiter, time)
                                + ls);
                        }
                    }
                }
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                if(in != null) in.close();
                if(out != null) out.close();
            } catch(IOException ex) {
                ex.printStackTrace();
                return;
            }
        }
    }

    /**
     * Converts a GPSL file time stamp and offset to an XMLGregorianCalendar
     * time.
     * 
     * @param time
     * @param offset The String offset representing hours.
     * @return
     */
    public static XMLGregorianCalendar getXMLGregorianCalendarFromTimeStamp(
        String time, String offset) {
        if(time == null || time.equalsIgnoreCase(GARMINTIME0)
            || time.length() != 19) {
            return null;
        }
        // Parse the time
        try {
            int month = Integer.parseInt(time.substring(0, 2));
            int date = Integer.parseInt(time.substring(3, 5));
            int year = Integer.parseInt(time.substring(6, 10));
            int hourOfDay = Integer.parseInt(time.substring(11, 13));
            int minute = Integer.parseInt(time.substring(14, 16));
            int second = Integer.parseInt(time.substring(17, 19));

            // Make a GregorianCalendar with the time
            GregorianCalendar gcal = new GregorianCalendar(
                TimeZone.getTimeZone("GMT"));
            gcal.set(year, month - 1, date, hourOfDay, minute, second);
            // The time in the GPSL file is local time corresponding to the
            // offset at the top. We need to convert it to UTC time. Convert in
            // minutes to allow fractional offsets used in some time zones.
            try {
                long minOffset = Math.round(-60. * Double.parseDouble(offset));
                gcal.add(GregorianCalendar.MINUTE, (int)minOffset);
            } catch(NumberFormatException ex) {
                // Do nothing
            }
            XMLGregorianCalendar xgcal = DatatypeFactory.newInstance()
                .newXMLGregorianCalendar(gcal);
            return xgcal;
        } catch(Throwable t) {
            return null;
        }
    }

    /**
     * Converts a XMLGregorianCalendar and an offset to a GPSL trackpoint time
     * string.
     * 
     * @param xgcal The XMLGregorianCalendar.
     * @param offset The double offset in hours.
     * @return
     */
    public static String getTimeFromXMLGregorianCalendar(
        XMLGregorianCalendar xgcal, double offset) {
        GregorianCalendar gcal = xgcal.toGregorianCalendar(
            TimeZone.getTimeZone("GMT"), null, null);
        gcal.add(GregorianCalendar.MINUTE, (int)Math.round(60. * offset));
        // Don't use SimpleDateFormat("MM/dd/yyyy HH:mm:ss") It will format with
        // the current time zone, Use the values for MONTH, etc. from the gcal.
        // (Can't use the ones from the xgcal because they can't be
        // incremented.)
        String time = String.format("%02d/%02d/%04d %02d:%02d:%02d",
            gcal.get(GregorianCalendar.MONTH) + 1,
            gcal.get(GregorianCalendar.DAY_OF_MONTH),
            gcal.get(GregorianCalendar.YEAR),
            gcal.get(GregorianCalendar.HOUR_OF_DAY),
            gcal.get(GregorianCalendar.MINUTE),
            gcal.get(GregorianCalendar.SECOND));
        return time;
    }
}
