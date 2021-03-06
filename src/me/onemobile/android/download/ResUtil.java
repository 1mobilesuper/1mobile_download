package me.onemobile.android.download;

import android.content.Context;

public class ResUtil {

	public static String getString(Context context, String name) {
		return context.getString(getStringId(context.getPackageName(), name));
	}

	public static int getId(String packageName, String name) {
		return getResourseIdByName(packageName, "id", name);
	}

	public static int getStringId(String packageName, String name) {
		return getResourseIdByName(packageName, "string", name);
	}

	public static int getDrawableId(String packageName, String name) {
		return getResourseIdByName(packageName, "drawable", name);
	}

	public static int getLayoutId(String packageName, String name) {
		return getResourseIdByName(packageName, "layout", name);
	}

	public static int getResourseIdByName(String packageName, String className, String name) {
		int id = 0;
		try {
			Class desireClass = Class.forName(packageName + ".R$" + className);
			if (desireClass != null)
				id = desireClass.getField(name).getInt(desireClass);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}

		return id;
	}
}
