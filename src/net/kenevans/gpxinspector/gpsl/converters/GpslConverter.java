package net.kenevans.gpxinspector.gpsl.converters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.math.BigDecimal;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import net.kenevans.gpx.GpxType;
import net.kenevans.gpx.RteType;
import net.kenevans.gpx.TrkType;
import net.kenevans.gpx.TrksegType;
import net.kenevans.gpx.WptType;
import net.kenevans.gpxinspector.converters.IGpxConverter;
import net.kenevans.gpxinspector.utils.Utils;
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
        // TODO
        // String fileExt = "." + Utils.getExtension(file);
        // if(fileExt != null) {
        // for(String ext : extensions) {
        // if(fileExt.equalsIgnoreCase(ext)) {
        // return true;
        // }
        // }
        // }
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
            Utils.errMsg("Unexpected end of file at line " + lineNum + ":\n"
                + file.getName());
            return null;
        }
        if(!line.equals(GPSLINK_ID)) {
            Utils.errMsg("Invalid GPSLink file (Bad ID) at line " + lineNum
                + ":\n" + file.getName());
            return null;
        }

        // Read timestamp
        line = in.readLine();
        lineNum++;
        if(line == null) {
            Utils.errMsg("Unexpected end of file at line " + lineNum + ":\n"
                + file.getName());
            return null;
        }

        // Delimiter
        line = in.readLine();
        lineNum++;
        if(line == null) {
            Utils.errMsg("Unexpected end of file at line " + lineNum + ":\n"
                + file.getName());
            return null;
        }
        tokens = line.split("=");
        if(tokens.length < 2 || !tokens[0].equals(DELIMITER)) {
            Utils.warnMsg("No delimiter found at line + lineNum "
                + ", assuming TAB:\n" + file.getName());
            delimiter = "\t";
        }
        delimiter = tokens[1];
        if(!delimiter.equals(",") && !delimiter.equals("\t")) {
            Utils.warnMsg("Invalid delimiter found at line + lineNum "
                + ", assuming TAB:\n" + file.getName());
            delimiter = "\t";
        }

        // GMTOffset
        line = in.readLine();
        lineNum++;
        if(line == null) {
            Utils.errMsg("Unexpected end of file at line " + lineNum + ":\n"
                + file.getName());
            return null;
        }
        tokens = line.split("=");
        if(tokens.length < 2 || !tokens[0].equals(GMTOFFSET)) {
            Utils.warnMsg("No " + GMTOFFSET + " found at " + lineNum
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
                    Utils.errMsg("Line " + lineNum
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
                    Utils.errMsg("Line " + lineNum + ": Cannot create route:\n"
                        + file.getName());
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
                    Utils.errMsg("Line " + lineNum + " Cannot create track:\n"
                        + file.getName());
                    error = true;
                    break;
                }
                trkType = new TrkType();
                trkType.setName(name);
                trkType.setDesc(name);
                if(trkType == null) {
                    Utils.errMsg("Line " + lineNum + ": Cannot create track:\n"
                        + file.getName());
                    error = true;
                    break;
                }
                gpx.getTrk().add(trkType);
                trkDataInProgress = true;
            } else if(startChar.equals("T")) {
                // TrackPoint
                if(tokens.length < 6) {
                    Utils.errMsg("Line " + lineNum + ": invalid trackpoint:\n"
                        + file.getName());
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
                    Utils.errMsg("Line " + lineNum
                        + ": Cannot create route waypoint:\n" + file.getName());
                    error = true;
                    break;
                }
                wptType.setLat(new BigDecimal(latitude));
                wptType.setLon(new BigDecimal(longitude));
                wptType.setEle(new BigDecimal(altitude));
                XMLGregorianCalendar xgcal = getXMLGregorianCalendar(time,
                    offset);
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
                                Utils.errMsg("Line " + lineNum
                                    + ": Cannot create track segment:\n"
                                    + file.getName());
                                error = true;
                                break;
                            }
                            trkType.getTrkseg().add(trksegType);
                        }
                    } else {
                        Utils.errMsg("Line " + lineNum
                            + " Found trackpoint without track:\n"
                            + file.getName());
                    }
                    trksegType.getTrkpt().add(wptType);
                }
            }
            // // DEBUG
            // if(false) {
            // System.out.println(startChar);
            // System.out.println("  name=" + name);
            // System.out.println("  trkType=" + trkType);
            // if(trkType != null) {
            // System.out.println("    " + trkType.getTrkseg().size()
            // + "  " + trkType.getName());
            // } else {
            // System.out.println("    null");
            // }
            // System.out.println("  trksegType=" + trksegType);
            // if(trksegType != null) {
            // System.out.println("    " + trksegType.getTrkpt().size());
            // } else {
            // System.out.println("    null");
            // }
            // System.out.println("  wptType=" + wptType);
            // if(wptType != null) {
            // System.out.println("    " + wptType.getName());
            // } else {
            // System.out.println("    null");
            // }
            // System.out.println();
            // }
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
        // TODO
        // GPXParser.save(
        // "GPX Inspector "
        // + SWTUtils.getPluginVersion("net.kenevans.gpxinspector"),
        // gpxType, file);
    }

    /**
     * Converts a GPSL time stamp to an XMLGregorianCalendar time. The GPSL time
     * is UTC.
     * 
     * @param time
     * @return
     */
    public static XMLGregorianCalendar getXMLGregorianCalendar(String time,
        String offset) {
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
            gcal.set(year, month, date, hourOfDay, minute, second);
            // The time in the GPSL file is local time corresponding to the
            // offset at the top. We need to convert it to UTC time. Convert in
            // seconds to allow fractional offsets used in some time zones.
            int secOffset = 0;
            try {
                secOffset = -3600*Integer.parseInt(offset);
                gcal.add(GregorianCalendar.SECOND, secOffset);
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
}
