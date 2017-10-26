package dk.enjens.xposedsettingsdemo;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Icon;
import android.os.Bundle;

import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

public class LoadHook implements IXposedHookLoadPackage {
    private Class<?> DashboardCategory;
    private Class<?> DashboardTile;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if(!lpparam.packageName.equals("com.android.settings"))
            return;

        DashboardCategory = findClass("com.android.settingslib.drawer.DashboardCategory", lpparam.classLoader);
        DashboardTile = findClass("com.android.settingslib.drawer.Tile", lpparam.classLoader);

        XposedBridge.log("Loaded app: " + lpparam.packageName);

        findAndHookMethod("com.android.settingslib.drawer.SettingsDrawerActivity", lpparam.classLoader, "getDashboardCategories", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                List<Object> dashBoardCategories = (List<Object>) getObjectField(param.thisObject, "sDashboardCategories");
                if(dashBoardCategories == null) {
                    XposedBridge.log("Error getting sDashboardCategories");
                    return;
                }
                // Check whether we already have an object in the list
                for(Object obj : dashBoardCategories) {
                    if(getAdditionalInstanceField(obj, "xposed_added") != null) {
                        return;
                    }
                }

                // Create the new category
                Object category = DashboardCategory.newInstance();
                setObjectField(category, "title", "XDA Settings");
                setIntField(category, "priority", 0);
                // Record that we created this category
                setAdditionalInstanceField(category, "xposed_added", true);

                // Load our own Icon to use as Settings Icon.
                Icon icon = Icon.createWithResource("dk.enjens.xposedsettingsdemo", R.mipmap.ic_launcher);
                if(icon == null)
                {
                    XposedBridge.log("Icon not found!?");
                    return;
                }
                // Android tints non-system settings items according to theme.
                // This disables that essentially.
                icon.setTintMode(PorterDuff.Mode.DST);

                // The intent that is called when the Tile is clicked
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // We don't have access to any useful context here, so hardcode our known exported activity
                ComponentName component = ComponentName.unflattenFromString("dk.enjens.xposedsettingsdemo/dk.enjens.xposedsettingsdemo.SettingsActivity");
                if(component == null)  {
                    XposedBridge.log("Component == null!?");
                    return;
                }
                intent.setComponent(component);

                // Create our settings tile under our new Category
                Object tile = newInstance(DashboardTile);
                setObjectField(tile, "title", "XposedSettingsDemo");
                setObjectField(tile, "summary", "This is a useless Setting Menu");
                setObjectField(tile, "intent", intent);
                setObjectField(tile, "icon", icon);
                setObjectField(tile, "category", "XDA Settings");
                setObjectField(tile, "metaData", new Bundle());
                setObjectField(tile, "extras", new Bundle());
                setAdditionalInstanceField(tile, "xposed_added", true);

                callMethod(category, "addTile", tile);
                dashBoardCategories.add(category);

                // Set the return value of the function to the updated list.
                // NOTE: not actually needed since it's returning a reference to the list
                // we modified.
                param.setResult(dashBoardCategories);

            }
        });


    }
}
