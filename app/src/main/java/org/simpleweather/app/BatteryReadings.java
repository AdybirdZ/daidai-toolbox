package org.simpleweather.app;

/** Convert only explicitly selected source units; never infer units from brand or magnitude. */
final class BatteryReadings {
    static final String CURRENT_UNIT="status_battery_current_unit",CHARGE_UNIT="status_battery_charge_unit";
    static double current(int raw,String unit,boolean reverse){
        if(raw==Integer.MIN_VALUE)return Double.NaN;
        double value="mA".equals(unit)?raw:raw/1000.0;
        return reverse?-value:value;
    }
    static double charge(int raw,String unit){
        if(raw==Integer.MIN_VALUE||raw<0)return Double.NaN;
        return "mAh".equals(unit)?raw:raw/1000.0;
    }
}
