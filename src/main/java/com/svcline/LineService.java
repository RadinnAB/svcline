package com.svcline;

import com.svcline.handlers.db.DbLineFacacde;
import com.routler.RError;
import com.svcline.models.LineItem;
import com.routler.RResponse;
import com.svcline.models.State;
import com.svcline.models.clocker.ClockerService;
import com.svcline.models.clocker.Operation;
import com.svcline.prodline.ProductionLine;
import com.svcline.prodline.Transition;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import static java.net.HttpURLConnection.*;

public class LineService {
    private final ProductionLine productionLine;
    private final ClockerService clockerService;

    public LineService(ProductionLine prodLine) {
        productionLine = prodLine;
        clockerService = new ClockerService(this.productionLine);
    }

    public RResponse stationItemStart(Transition transition) throws ExecutionException, InterruptedException {
        if (!transition.validate())
            return new RResponse(HTTP_BAD_REQUEST, new RError("Line validation error for station start: " + transition));

        RResponse rResponse;
        String itemId = transition.getId();

        try {
            DbLineFacacde dbLineFacacde = new DbLineFacacde(productionLine.getFirestore(), productionLine.getProps().isLiveEnv());
            LineItem actualItemDb = dbLineFacacde.getFor(itemId);
            LineItem lineItemIn = new LineItem(transition);

            /* Basic checks must be performed before we advance to the next station, those being:
             * 1. An item must have previously been created, meaning stored in the DB (with all passed checks previously)
             * 2. The new state the item moves to is provided in the body, which we validate,
             * 3. Body ID must match Path ID. The Path ID is the master!
             */

            if (actualItemDb == null) { // Check and validate the original item
                rResponse = new RResponse(HTTP_NOT_FOUND,
                                          new RError("Original item not found for id " + itemId + "."));
            } else if (actualItemDb.isDone()) {
                rResponse = new RResponse(HTTP_CONFLICT,
                                          new RError("Cannot produce an item twice."));
            } else if (!lineItemIn.validate()) { // Check and validate the "new" item
                rResponse = new RResponse(HTTP_BAD_REQUEST,
                                          new RError("Failed to validate item for id " + itemId + ". Check arguments."));
            } else if (!lineItemIn.getId().equalsIgnoreCase(itemId)) { // Check and validate the "new" item
                rResponse = new RResponse(HTTP_CONFLICT,
                                          new RError("Body ID not matching path ID. Check arguments."));
            } else {
                // Item must exist and not be in START
                clockItemStartTime(transition);

                // We handle the line transition in the production line
                LineItem verifiedItem = productionLine.fromStation(actualItemDb, lineItemIn);

                dbLineFacacde.set(verifiedItem);

                rResponse = new RResponse(verifiedItem);
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
            rResponse = new RResponse(HTTP_CONFLICT, new RError(e.getMessage()));
        } catch (InstantiationException e) {
            e.printStackTrace();
            rResponse = new RResponse(HTTP_UNAVAILABLE, new RError(e.getMessage()));
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            rResponse = new RResponse(HTTP_INTERNAL_ERROR, new RError(e.getMessage()));
        }

        return rResponse;
    }

    public RResponse stationItemStop(Transition transition) {
        if (!transition.validate())
            return new RResponse(HTTP_BAD_REQUEST, new RError("Line validation error for station stop: " + transition));

        RResponse rResponse;
        String itemId = transition.getId();

        try {
            DbLineFacacde dbLineFacacde = new DbLineFacacde(productionLine.getFirestore(), productionLine.getProps().isLiveEnv());
            LineItem actualItemDb = dbLineFacacde.getFor(itemId);
            LineItem lineItemIn = new LineItem(transition);

            /* Basic checks must be performed before we advance to the next station, those being:
             * 1. An item must have previously been created, meaning stored in the DB (with all passed checks previously)
             * 2. The new state the item moves to is provided in the body, which we validate,
             * 3. Body ID must match Path ID. The Path ID is the master!
             */

            if (actualItemDb == null) { // Check and validate the original item
                rResponse = new RResponse(HTTP_NOT_FOUND,
                                          new RError("Original item not found for id " + itemId + "."));
            } else if (!lineItemIn.validate()) { // Check and validate the "new" item
                rResponse = new RResponse(HTTP_BAD_REQUEST,
                                          new RError("Failed to validate item for id " + itemId + ". Check arguments."));
            } else if (!lineItemIn.getId().equalsIgnoreCase(itemId)) { // Check and validate the "new" item
                rResponse = new RResponse(HTTP_CONFLICT,
                                          new RError("Body ID not matching path ID. Check arguments."));
            } else {
                // We handle the line transition in the production line
                LineItem verifiedItem = productionLine.toNextStation(actualItemDb, lineItemIn);

                dbLineFacacde.set(verifiedItem);

                clockItemStopTime(transition);

                rResponse = new RResponse(verifiedItem);
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
            rResponse = new RResponse(HTTP_CONFLICT, new RError(e.getMessage()));
        } catch (InstantiationException e) {
            e.printStackTrace();
            rResponse = new RResponse(HTTP_UNAVAILABLE, new RError(e.getMessage()));
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            rResponse = new RResponse(HTTP_INTERNAL_ERROR, new RError(e.getMessage()));
        }

        return rResponse;
    }

    public RResponse startProduction(Transition transitionIn) {
        // When we create a transition we're only interested in the ID.
        if (transitionIn.getId() == null)
            return new RResponse(HTTP_BAD_REQUEST, new RError("Line initiation validation error for: " + transitionIn));

        RResponse rResponse;
        String itemId = transitionIn.getId();
        Transition transition = new Transition(transitionIn);
        transition.setState(State.CREATED);

        try {
            DbLineFacacde dbLineFacacde = new DbLineFacacde(productionLine.getFirestore(), productionLine.getProps().isLiveEnv());

            if (dbLineFacacde.getFor(itemId) != null) {
                rResponse = new RResponse(HTTP_CONFLICT, new RError("Failed to create existing object for existing id: " + itemId));
            } else {    // Validations passed, create document
                clockItemStartTime(transition);
                LineItem lineItem = productionLine.startProduction(itemId);

                dbLineFacacde.set(lineItem);

                rResponse = new RResponse(HTTP_CREATED, lineItem);
            }
        } catch (InstantiationException e) {
            e.printStackTrace();
            rResponse = new RResponse(HTTP_UNAVAILABLE, new RError(e.getMessage()));
        } catch (ExecutionException | InterruptedException | IllegalArgumentException e) {
            e.printStackTrace();
            rResponse = new RResponse(HTTP_INTERNAL_ERROR, new RError(e.getMessage()));
        }

        return rResponse;
    }

    public RResponse getItem(String itemId) {
        if (itemId == null)
            return new RResponse(HTTP_BAD_REQUEST, new RError("itemId cannot be null!"));

        RResponse rResponse;
        try {
            DbLineFacacde dbLineFacacde = new DbLineFacacde(productionLine.getFirestore(), productionLine.getProps().isLiveEnv());
            LineItem lineItem = dbLineFacacde.getFor(itemId);

            if (lineItem != null) {
                rResponse = new RResponse(lineItem);
            } else {
                rResponse = new RResponse(HTTP_NOT_FOUND, new RError("Item not found for provided id: " + itemId));
            }
        } catch (Exception e) {
            e.printStackTrace();
            rResponse = new RResponse(HTTP_INTERNAL_ERROR, new RError(e.getMessage()));
        }

        return rResponse;
    }

    public RResponse getAllItems() {
        RResponse rResponse;

        try {
            DbLineFacacde dbLineFacacde = new DbLineFacacde(productionLine.getFirestore(), productionLine.getProps().isLiveEnv());
            ArrayList<LineItem> lineItems = dbLineFacacde.getAll();

            if (lineItems != null) {
                rResponse = new RResponse(lineItems);
            } else {
                rResponse = new RResponse(HTTP_NOT_FOUND, new RError("No items found! Try adding some first ;)"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            rResponse = new RResponse(HTTP_INTERNAL_ERROR, new RError(e.getMessage()));
        }

        return rResponse;
    }

    public RResponse deleteLineEntryForItem(String itemId) {
        if (this.productionLine.getProps().isLiveEnv())
            return new RResponse(HTTP_FORBIDDEN, new RError("Not allowed to delete entry in production."));

        RResponse rResponse;
        try {
            DbLineFacacde dbLineFacacde = new DbLineFacacde(productionLine.getFirestore(), productionLine.getProps().isLiveEnv());

            LineItem lineItem = dbLineFacacde.getFor(itemId);
            if (lineItem == null) {
                rResponse = new RResponse(HTTP_CONFLICT, new RError("Failed to delete non-existing object, for id: " + itemId));
            } else {
                dbLineFacacde.deleteFor(itemId);
                clockerService.deleteFor(itemId);

                rResponse = new RResponse(HTTP_OK, "Deleted: " + lineItem.getId());
            }
        } catch (ExecutionException | InterruptedException | IllegalArgumentException e) {
            e.printStackTrace();
            rResponse = new RResponse(HTTP_INTERNAL_ERROR, new RError(e.getMessage()));
        }

        return rResponse;
    }

    private void clockItemStartTime(Transition transition) throws ExecutionException, InterruptedException {
        String itemId = transition.getId();
        String stationId = transition.getCurrentStationId() == null ? productionLine.getStartStationId() : transition.getCurrentStationId();

        clockerService.setTime(itemId, stationId, Operation.START);
    }
    private void clockItemStopTime(Transition transition) throws ExecutionException, InterruptedException {
        String itemId = transition.getId();

        clockerService.setTime(itemId, transition.getCurrentStationId(), Operation.STOP);
    }
}
