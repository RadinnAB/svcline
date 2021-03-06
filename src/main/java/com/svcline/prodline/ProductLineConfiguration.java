package com.svcline.prodline;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.annotation.Exclude;
import com.svcline.models.Action;
import com.svcline.models.State;
import com.svcline.models.Station;
import com.svcline.models.StationType;
import com.svcline.prodline.db.DbProdLineConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProductLineConfiguration {
    private String version;
    private StationMap configuredStationMap;
    private StationOrderMap configuredStationOrder;
    private Double estimatedItemProductionTime;

    @Exclude
    private Firestore firestore;

    public ProductLineConfiguration() {
    }

    public ProductLineConfiguration(Firestore firestore) throws IOException {
        this.configuredStationMap = new StationMap();
        this.configuredStationOrder = new StationOrderMap();
        this.firestore = firestore;
        this.estimatedItemProductionTime = 0.0;
    }

    public Double getEstimatedItemProductionTime() {
        return estimatedItemProductionTime;
    }

    public void setEstimatedItemProductionTime(Double estimatedItemProductionTime) {
        this.estimatedItemProductionTime = estimatedItemProductionTime;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public StationMap getConfiguredStationMap() {
        return configuredStationMap;
    }

    public void setConfiguredStationMap(StationMap configuredStationMap) {
        this.configuredStationMap = configuredStationMap;
    }

    public StationOrderMap getConfiguredStationOrder() {
        return configuredStationOrder;
    }

    public void setConfiguredStationOrder(StationOrderMap configuredStationOrder) {
        this.configuredStationOrder = configuredStationOrder;
    }

    public void writeToDb(String configId) {
        DbProdLineConfiguration prodLineConfiguration = new DbProdLineConfiguration(firestore);
        prodLineConfiguration.write(configId, this);
    }

    public void loadFromDb(String configId) {
        DbProdLineConfiguration prodLineConfiguration = new DbProdLineConfiguration(firestore);
        ProductLineConfiguration plc = prodLineConfiguration.read(configId);

        this.configuredStationMap = plc.configuredStationMap;
        this.configuredStationOrder = plc.getConfiguredStationOrder();
        this.estimatedItemProductionTime = plc.getEstimatedItemProductionTime();
    }

    public void loadTestConfiguration() {
        Action pass = new Action("Next", State.PASSED);
        Action failed = new Action("Failed", State.FAILED);
        Action retry = new Action("Retry", State.RETRIED);
        Action scrap = new Action("Scrap item", State.SCRAPED);

        Station station1 = new Station("1001", "Start Station", StationType.START,
                                       new ArrayList<>(List.of(pass, failed)), 1.0);

        Station station2 = new Station("1002", "Second Station", StationType.PRODUCTION,
                                       new ArrayList<>(List.of(pass, failed)), 2.0);

        Station station3 = new Station("1003", "Third Station", StationType.PRODUCTION,
                                       new ArrayList<>(List.of(pass, failed)), 3.0);

        Station station4 = new Station("1004", "End Station", StationType.END,
                                       new ArrayList<>(List.of(pass, failed)), 4.0);

        //Station station5 = new Station("2005", "End Station", StationType.END,
        //                               new ArrayList<>(List.of(State.PASS, State.FAIL)));

        Station serviceStation = new Station("2001", "Service Station", StationType.SERVICE,
                                             new ArrayList<>(List.of(pass, retry, scrap)), 5.0);
        configuredStationMap.addStation(station1);
        configuredStationMap.addStation(station2);
        configuredStationMap.addStation(station3);
        configuredStationMap.addStation(station4);
        configuredStationMap.addStation(serviceStation);

        configuredStationOrder.addStationTransition(station1.getId(), station2.getId());
        configuredStationOrder.addStationTransition(station2.getId(), station3.getId());
        configuredStationOrder.addStationTransition(station3.getId(), station4.getId());

        estimatedItemProductionTime = 15.0;
    }

    @Override
    public String toString() {
        return "ProductLineConfiguration{" +
               "version='" + version + '\'' +
               ", configuredStationMap=" + configuredStationMap +
               ", configuredStationOrder=" + configuredStationOrder +
               '}';
    }
}