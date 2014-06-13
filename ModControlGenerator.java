import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.Stack;

import javax.swing.JFileChooser;

import ksp.Part;

public class ModControlGenerator {

	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	
	final public static boolean MODE_BLACKLIST = true;
	final public static boolean MODE_WHITELIST = false;
	
	public static String getType(String name) {
		int lastDot = name.lastIndexOf('.');
		if(lastDot == -1 ) return "";
		return name.substring(lastDot+1);
	}
	
	public static LinkedList<String> parse(File f) {
		String type = getType(f.getName());
		if(type.compareTo("cfg") != 0) return null;
		Scanner sc;
		try {
			sc = new Scanner(f);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		LinkedList<String> ret = new LinkedList<String>();
		String prevline = "";
		Stack<String> stack = new Stack<String>();
		while(sc.hasNext()) {
			String line = sc.nextLine();
			line = line.trim();
			if(line.compareTo("{") == 0) {
				stack.push(prevline);
				continue;
			} else if (line.compareTo("}") == 0) {
				if(stack.size() > 0) stack.pop();
				continue;
			} else if (line.trim().endsWith("{")) {
				String modline = line.trim().substring(0, line.length()-2);
				modline = modline.trim();
				if(modline.compareToIgnoreCase("part") == 0) {
					stack.push(modline);
				}
			}
			if(line.length() < 4) continue;
			if(line.substring(0, 4).compareTo("name") == 0 && stack.size() > 0 && stack.peek().compareToIgnoreCase("part") == 0) {
				String name = line.trim();
				int eqPos = name.lastIndexOf('=');
				if(eqPos == -1) break;
				name = name.substring(eqPos+1);
				name = name.trim();
				ret.add(name.replace('_', '.'));
			}
			prevline = line;
		}
		
		sc.close();
		return ret;
	}
	
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	
	public static String hashStr(File f) {
		try {
			byte [] buf = new byte[1024*1024];
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			FileInputStream stream = new FileInputStream(f);
			while(stream.available() > 0) {
				int numRead = stream.read(buf);
				digest.update(buf, 0, numRead);
			}
			stream.close();
			byte [] finalDigest = digest.digest();
			return bytesToHex(finalDigest);
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}
	
	public static LinkedList<String> findPartsRecursive(File folder) {
		if(!folder.isDirectory()) return null;
		
		LinkedList<String> ret = new LinkedList<String>();
		for (File f : folder.listFiles()) {
			if(f.isDirectory()) {
				ret.addAll(findPartsRecursive(f));
			} else if (getType(f.getName()).compareTo("cfg") == 0) {
				LinkedList<String> names = parse(f);
				if(names == null) continue;
				ret.addAll(names);
			}
		}
		return ret;
	}
	
	public static LinkedList<String> findPartsRecursive(File [] folders) {
		LinkedList<String> ret = new LinkedList<String>();
		
		for(File f : folders) {
			if(!f.isDirectory()) continue;
			ret.addAll(findPartsRecursive(f));
		}
		
		return ret;
	}
	
	public static LinkedList<String> findDllsRecursive(File folder, boolean strict) {
		if(!folder.isDirectory()) return null;
		
		LinkedList<String> ret = new LinkedList<String>();
		for (File f : folder.listFiles()) {
			if(f.isDirectory()) {
				ret.addAll(findDllsRecursive(f, strict));
			} else if (getType(f.getName()).compareTo("dll") == 0) {
				int lastIndex = f.getAbsolutePath().lastIndexOf("GameData\\");
				if(lastIndex == -1) continue;
				
				lastIndex += "GameData\\".length();
				String kspRootPath = f.getAbsolutePath().substring(lastIndex);
				if(strict) kspRootPath = kspRootPath + "=" + hashStr(f);
				ret.add(kspRootPath);
			}
		}
		return ret;
	}
	
	public static LinkedList<String> findDllsRecursive(File [] folders, boolean strict) {
		LinkedList<String> ret = new LinkedList<String>();
		
		for(File f : folders) {
			if(!f.isDirectory()) continue;
			ret.addAll(findDllsRecursive(f, strict));
		}
		
		return ret;
	}
	
	public static void generateModControl(LinkedList<String> required_files,LinkedList<String> optional_files,
									LinkedList<String> black_whitelist, LinkedList<String> partslist,
									boolean blacklist, String outputFile)
	{
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, false));
			writer.write("#You can comment by starting a line with a #, these are ignored by the server.\n"
						+ "#Commenting will NOT work unless the line STARTS with a '#'.\n"
						+ "#You can also indent the file with tabs or spaces.\n"
						+ "#Sections supported are required-files, optional-files, partslist, resource-blacklist and resource-whitelist.\n"
						+ "#The client will be required to have the files found in required-files, "
						+ "and they must match the SHA hash if specified (this is where part mod files and play-altering files should go, "
						+ "like KWRocketry or Ferram Aerospace Research#The client may have the files found in optional-files, "
						+ "but IF they do then they must match the SHA hash (this is where mods that do not affect other players should go, "
						+ "like EditorExtensions or part catalogue managers\n"
						+ "#You cannot use both resource-blacklist AND resource-whitelist in the same file.\n"
						+ "#resource-blacklist bans ONLY the files you specify\n"
						+ "#resource-whitelist bans ALL resources except those specified in the resource-whitelist section OR in the SHA sections. "
						+ "A file listed in resource-whitelist will NOT be checked for SHA hash. "
						+ "This is useful if you want a mod that modifies files in its own directory as you play.\n"
						+ "#Each section has its own type of formatting. Examples have been given.\n"
						+ "#Sections are defined as follows:\n"
						+ "\n"
						+ "!required-files\n"
						+ "#To generate the SHA256 of a file you can use a utility such as this one: "
						+ "http://hash.online-convert.com/sha256-generator (use the 'hex' string), or use sha256sum on linux.\n"
						+ "#File paths are read from inside GameData.\n"
						+ "#If there is no SHA256 hash listed here (i.e. blank after the equals sign or no equals sign), SHA matching will not be enforced.\n"
						+ "#You may not specify multiple SHAs for the same file. Do not put spaces around equals sign. Follow the example carefully.\n"
						+ "#Syntax:\n"
						+ "#[File Path]=[SHA] or [File Path]\n"
						+ "#Example: MechJeb2/Plugins/MechJeb2.dll=B84BB63AE740F0A25DA047E5EDA35B26F6FD5DF019696AC9D6AF8FC3E031F0B9\n"
						+ "#Example: MechJeb2/Plugins/MechJeb2.dll\n");
			
			if(required_files != null)
				for(String s : required_files)
					writer.write(s + "\n");

			writer.write('\n');
			
			writer.write("!optional-files\n"
					+ "#Formatting for this section is the same as the 'required-files' section\n");

			if(optional_files != null)
				for(String s : optional_files)
					writer.write(s + "\n");

			writer.write('\n');
			
			if(blacklist) {
				writer.write("!resource-blacklist\n"
							+ "#!resource-whitelist\n");
			} else {
				writer.write("#!resource-blacklist\n"
							+ "!resource-whitelist\n");
			}
			
			writer.write("#Only select one of these modes.\n"
						+ "#Resource blacklist: clients will be allowed to use any dll's, So long as they are not listed in this section\n"
						+ "#Resource whitelist: clients will only be allowed to use dll's listed here or in the 'required-files' and 'optional-files' sections.\n"
						+ "#Syntax:\n"
						+ "#[File Path]\n"
						+ "#Example: MechJeb2/Plugins/MechJeb2.dll\n");

			
			if(black_whitelist != null)
				for(String s : black_whitelist)
					writer.write(s + "\n");
			
			writer.write('\n');
			
			writer.write("!partslist\n"
						+ "#This is a list of parts to allow users to put on their ships.\n"
						+ "#If a part the client has doesn't appear on this list, they can still join the server but not use the part.\n"
						+ "#The default stock parts have been added already for you.\n"
						+ "#To add a mod part, add the name from the part's .cfg file. The name is the name from the PART{} section, where underscores are replaced with periods.\n"
						+ "#[partname]\n"
						+ "#Example: mumech.MJ2.Pod (NOTE: In the part.cfg this MechJeb2 pod is named mumech_MJ2_Pod. The _ have been replaced with .)\n");
			
			writer.write("StandardCtrlSrf\n" + "CanardController\n" + "noseCone\n" + "AdvancedCanard\n"
						+ "airplaneTail\n" + "deltaWing\n" + "noseConeAdapter\n" + "rocketNoseCone\n"
						+ "smallCtrlSrf\n" + "standardNoseCone\n" + "sweptWing\n" + "tailfin\n"
						+ "wingConnector\n" + "winglet\n" + "R8winglet\n" + "winglet3\n"
						+ "Mark1Cockpit\n" + "Mark2Cockpit\n" + "Mark1-2Pod\n" + "advSasModule\n"
						+ "asasmodule1-2\n" + "avionicsNoseCone\n" + "crewCabin\n" + "cupola\n"
						+ "landerCabinSmall\n" + "mark3Cockpit\n" + "mk1pod\n" + "mk2LanderCabin\n"
						+ "probeCoreCube\n" + "probeCoreHex\n" + "probeCoreOcto\n" + "probeCoreOcto2\n"
						+ "probeCoreSphere\n" + "probeStackLarge\n" + "probeStackSmall\n" + "sasModule\n"
						+ "seatExternalCmd\n" + "rtg\n" + "batteryBank\n" + "batteryBankLarge\n"
						+ "batteryBankMini\n" + "batteryPack\n" + "ksp.r.largeBatteryPack\n" + "largeSolarPanel\n"
						+ "solarPanels1\n" + "solarPanels2\n" + "solarPanels3\n" + "solarPanels4\n"
						+ "solarPanels5\n" + "JetEngine\n" + "engineLargeSkipper\n" + "ionEngine\n"
						+ "liquidEngine\n" + "liquidEngine1-2\n" + "liquidEngine2\n" + "liquidEngine2-2\n"
						+ "liquidEngine3\n" + "liquidEngineMini\n" + "microEngine\n" + "nuclearEngine\n"
						+ "radialEngineMini\n" + "radialLiquidEngine1-2\n" + "sepMotor1\n" + "smallRadialEngine\n"
						+ "solidBooster\n" + "solidBooster1-1\n" + "toroidalAerospike\n" + "turboFanEngine\n"
						+ "MK1Fuselage\n" + "Mk1FuselageStructural\n" + "RCSFuelTank\n" + "RCSTank1-2\n"
						+ "rcsTankMini\n" + "rcsTankRadialLong\n" + "fuelTank\n" + "fuelTank1-2\n"
						+ "fuelTank2-2\n" + "fuelTank3-2\n" + "fuelTank4-2\n" + "fuelTankSmall\n"
						+ "fuelTankSmallFlat\n" + "fuelTank.long\n" + "miniFuelTank\n" + "mk2Fuselage\n"
						+ "mk2SpacePlaneAdapter\n" + "mk3Fuselage\n" + "mk3spacePlaneAdapter\n" + "radialRCSTank\n"
						+ "toroidalFuelTank\n" + "xenonTank\n" + "xenonTankRadial\n" + "adapterLargeSmallBi\n"
						+ "adapterLargeSmallQuad\n" + "adapterLargeSmallTri\n" + "adapterSmallMiniShort\n" + "adapterSmallMiniTall\n"
						+ "nacelleBody\n" + "radialEngineBody\n" + "smallHardpoint\n" + "stationHub\n"
						+ "structuralIBeam1\n" + "structuralIBeam2\n" + "structuralIBeam3\n" + "structuralMiniNode\n"
						+ "structuralPanel1\n" + "structuralPanel2\n" + "structuralPylon\n" + "structuralWing\n"
						+ "strutConnector\n" + "strutCube\n" + "strutOcto\n" + "trussAdapter\n"
						+ "trussPiece1x\n" + "trussPiece3x\n" + "CircularIntake\n" + "landingLeg1\n"
						+ "landingLeg1-2\n" + "RCSBlock\n" + "stackDecoupler\n" + "airScoop\n"
						+ "commDish\n" + "decoupler1-2\n" + "dockingPort1\n" + "dockingPort2\n"
						+ "dockingPort3\n" + "dockingPortLarge\n" + "dockingPortLateral\n" + "fuelLine\n"
						+ "ladder1\n" + "largeAdapter\n" + "largeAdapter2\n" + "launchClamp1\n"
						+ "linearRcs\n" + "longAntenna\n" + "miniLandingLeg\n" + "parachuteDrogue\n"
						+ "parachuteLarge\n" + "parachuteRadial\n" + "parachuteSingle\n" + "radialDecoupler\n"
						+ "radialDecoupler1-2\n" + "radialDecoupler2\n" + "ramAirIntake\n" + "roverBody\n"
						+ "sensorAccelerometer\n" + "sensorBarometer\n" + "sensorGravimeter\n" + "sensorThermometer\n"
						+ "spotLight1\n" + "spotLight2\n" + "stackBiCoupler\n" + "stackDecouplerMini\n"
						+ "stackPoint1\n" + "stackQuadCoupler\n" + "stackSeparator\n" + "stackSeparatorBig\n"
						+ "stackSeparatorMini\n" + "stackTriCoupler\n" + "telescopicLadder\n" + "telescopicLadderBay\n"
						+ "SmallGearBay\n" + "roverWheel1\n" + "roverWheel2\n" + "roverWheel3\n"
						+ "wheelMed\n" + "flag\n" + "kerbalEVA\n" + "mediumDishAntenna\n"
						+ "GooExperiment\n" + "science.module\n" + "RAPIER\n" + "Large.Crewed.Lab\n" 
						+ "GrapplingDevice\n" + "LaunchEscapeSystem\n" + "MassiveBooster\n"  + "PotatoRoid\n" 
						+ "Size2LFB\n" + "Size3AdvancedEngine\n" + "size3Decoupler\n" + "Size3EngineCluster\n"
						+ "Size3LargeTank\n" + "Size3MediumTank\n" + "Size3SmallTank\n" + "Size3to2Adapter\n");
			
			if(partslist != null)
				for(String s : partslist)
					writer.write(s + "\n");
			
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Error writing file " + outputFile);
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("Select mods to add (folders in the GameData folder)");
		chooser.setMultiSelectionEnabled(true);
		int ret = chooser.showOpenDialog(null);
		if(ret != JFileChooser.APPROVE_OPTION) return;
		
		File [] files = chooser.getSelectedFiles();
		LinkedList<String> parts = findPartsRecursive(files);
		LinkedList<String> dlls = findDllsRecursive(files, true);
		
		chooser = new JFileChooser();
		chooser.setDialogTitle("Save As");
		ret = chooser.showSaveDialog(null);
		if(ret != JFileChooser.APPROVE_OPTION) return;
		File outfile = chooser.getSelectedFile();
		if(!outfile.canWrite()) return;
		generateModControl(dlls, null, null, parts, MODE_BLACKLIST, outfile.getAbsolutePath());
	}

}
