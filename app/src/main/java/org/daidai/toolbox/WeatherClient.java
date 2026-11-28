package org.daidai.toolbox;
import org.json.JSONObject;
import java.util.List;
/** Shared weather data contract. Network implementation belongs to the optional weather app. */
public interface WeatherClient {
    JSONObject forecast(City city)throws Exception;
    List<City> search(String query)throws Exception;
    interface RequestPolicy {void reserve()throws java.io.IOException;void blockUntil(long timestamp);}
}
