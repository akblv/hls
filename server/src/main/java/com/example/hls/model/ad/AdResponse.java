package com.example.hls.model.ad;

import java.util.ArrayList;
import java.util.List;

public class AdResponse {
    private String id;
    private List<AdDetails> adDetailsList = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<AdDetails> getAdDetailsList() {
        return adDetailsList;
    }

    public void setAdDetailsList(List<AdDetails> adDetailsList) {
        this.adDetailsList = adDetailsList;
    }
}
