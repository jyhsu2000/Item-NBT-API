package de.tr7zw.nbtapi.plugin.tests.compounds;

import de.tr7zw.changeme.nbtapi.NBTCompoundList;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import de.tr7zw.changeme.nbtapi.NBTList;
import de.tr7zw.changeme.nbtapi.NBTListCompound;
import de.tr7zw.changeme.nbtapi.NbtApiException;
import de.tr7zw.nbtapi.plugin.tests.Test;

public class ListTest implements Test{

	@Override
	public void test() throws Exception {
		NBTContainer comp = new NBTContainer();

		//Strings
		NBTList<String> list = comp.getStringList("testlist");
		list.add("test1");
		list.add("test2");
		list.add("test3");
		list.add("test4");
		list.set(2, "test42");
		list.remove(1);
		if (!list.get(1).equals("test42") || list.size() != 3) {
			throw new NbtApiException("The String-list did not match what it should have looked like.");
		}

		//Compound
		NBTCompoundList taglist = comp.getCompoundList("complist");
		NBTListCompound lcomp = taglist.addCompound();
		lcomp.setDouble("double1", 0.3333);
		lcomp.setInteger("int1", 42);
		lcomp.setString("test1", "test1");
		lcomp.setString("test2", "test2");
		lcomp.remove("test1");

		taglist = null;
		lcomp = null;

		taglist = comp.getCompoundList("complist");
		if (taglist.size() == 1) {
			lcomp = taglist.get(0);
			if (lcomp.getKeys().size() != 3) {
				throw new NbtApiException("Wrong key amount in Taglist (" + lcomp.getKeys().size()+ ")!");
			} else if (!(lcomp.getDouble("double1") == 0.3333 && lcomp.getInteger("int1") == 42
					&& lcomp.getString("test2").equals("test2") && !lcomp.hasKey("test1"))) {
				throw new NbtApiException("One key in the Taglist changed! The Item-NBT-API may not work!");
			}
		} else {
			throw new NbtApiException("Taglist is empty! The Item-NBT-API may not work!");
		}
		
		//Integer
		NBTList<Integer> intlist = comp.getIntegerList("inttest");
		intlist.add(42);
		intlist.add(69);
		if(intlist.size() == 2 && intlist.get(0) == 42 && intlist.get(1) == 69) {
			//ok
		}else {
			throw new NbtApiException("IntList is not correct!");
		}
		
		
	}

}
