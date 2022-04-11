package com.svcline.handlers;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import com.svcline.LineService;
import com.svcline.models.Context;
import com.svcline.models.Error;
import com.svcline.models.LineResponse;
import com.svcline.prodline.Transition;
import com.svcline.routler.Route;
import com.svcline.routler.Routeable;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_IMPLEMENTED;

public class LineHandler implements Routeable {
    private static final String ITEM_ID = "{itemId}";
    private static final Gson gson = new Gson();
    private Context context;

    @Override
    public void setContext(Context context) {
        this.context = context;
    }

    @Override
    public LineResponse get(Route route, HttpRequest request, HttpResponse response) {
        LineResponse lineResponse;

        if (route.isNaked()) {
            lineResponse = getAll();
        } else {
            lineResponse = getFor(route.getPathVal(ITEM_ID));
        }

        return lineResponse;
    }

    @Override
    public LineResponse put(Route route, HttpRequest request, HttpResponse response) {
        String itemId = route.getPathVal(ITEM_ID);
        if (itemId == null || itemId.isBlank()) {
            return new LineResponse(HTTP_BAD_REQUEST, new Error("Error while updating item due to miss-crafted id."));
        }

        Transition transition;

        try {
            transition = gson.fromJson(request.getReader(), Transition.class);
        } catch (IOException e) {
            e.printStackTrace();
            return new LineResponse(HTTP_BAD_REQUEST, new Error("Unexpected error while updating item. Please try again."));
        }

        LineService lineService = new LineService(context.getProductionLine());
        return lineService.toNext(transition);
    }

    @Override
    public LineResponse patch(Route route, HttpRequest request, HttpResponse response) {
        String itemId = route.getPathVal(ITEM_ID);
        if (itemId == null || itemId.isBlank()) {
            return new LineResponse(HTTP_BAD_REQUEST, new Error("Error while updating item due to miss-crafted id."));
        }

        Transition transition;

        try {
            transition = gson.fromJson(request.getReader(), Transition.class);
        } catch (IOException e) {
            e.printStackTrace();
            return new LineResponse(HTTP_BAD_REQUEST, new Error("Unexpected error while updating item. Please try again."));
        }

        LineService lineService = new LineService(context.getProductionLine());

        try {
            lineService.prepareItem(transition);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return new LineResponse(HTTP_BAD_REQUEST, new Error("Item not prepared for processing please prepare."));
        }

        return new LineResponse("Item prepared for production.");
    }

    @Override
    public LineResponse post(Route route, HttpRequest request, HttpResponse response) {
        Transition transition;

        try {
            transition = gson.fromJson(request.getReader(), Transition.class);
        } catch (IOException e) {
            e.printStackTrace();
            return new LineResponse(HTTP_BAD_REQUEST, new Error("Unexpected error while creating item. Please try again."));
        }

        LineService lineService = new LineService(context.getProductionLine());
        return lineService.create(transition);
    }

    @Override
    public LineResponse delete(Route route, HttpRequest request, HttpResponse response) {
        return new LineResponse(HTTP_NOT_IMPLEMENTED, new Error("DELETE method not implemented"));
    }

    private LineResponse getFor(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return new LineResponse(HTTP_BAD_REQUEST, new Error("Error while looking up unit due to miss-crafted id."));
        }

        LineService lineService = new LineService(context.getProductionLine());
        return lineService.getItem(itemId);
    }

    private LineResponse getAll() {
        LineService lineService = new LineService(context.getProductionLine());
        return lineService.getAllItems();
    }
}
