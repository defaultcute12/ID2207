package com.sep.services;

import com.sep.domain.EPRStatus;
import com.sep.domain.EventPlanningRequest;
import com.sep.repositories.EventPlanningRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class EventPlanningRequestServiceImpl implements EventPlanningRequestService {

    @Autowired
    private EventPlanningRequestRepository eprRepository;

    @Override
    public Iterable<EventPlanningRequest> listAllEventPlanningRequests() {
        return eprRepository.findAll();
    }

    @Override
    public EventPlanningRequest getEventPlanningRequestById(Long id) {
        return eprRepository.findOne(id);
    }

    @Override
    public EventPlanningRequest saveEventPlanningRequest(EventPlanningRequest product) {
        return eprRepository.save(product);
    }

    @Override
    public List<EventPlanningRequest> getEventPlanningRequestsByStatus(EPRStatus status) {
        return eprRepository.findByStatus(status);
    }

}
