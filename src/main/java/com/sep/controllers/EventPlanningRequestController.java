package com.sep.controllers;

import com.sep.domain.*;
import com.sep.repositories.ClientRepository;
import com.sep.repositories.TeamTaskRepository;
import com.sep.repositories.UserRepository;
import com.sep.services.AuditService;
import com.sep.services.EventPlanningRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

@Controller
@RequestMapping("/epr")
public class EventPlanningRequestController {
    Logger log = LoggerFactory.getLogger(EventPlanningRequestController.class);

    @Autowired
    private EventPlanningRequestService eprService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamTaskRepository taskRepository;

    @RequestMapping(value = {"/list"}, method = RequestMethod.GET)
    public String list(Model model){
        Iterable<EventPlanningRequest> eprs = eprService.listAllEventPlanningRequests();
        boolean allTaskscompleted = true;
        done:
        for (EventPlanningRequest epr : eprs) {
            Set<TeamTask> tasks = taskRepository.findByEpr(epr);
            for (TeamTask task : tasks) {
                if (task.getStatus() != TaskStatus.VERIFIED) {
                    allTaskscompleted = false;
                    break done;
                }
            }
        }
        model.addAttribute("eprs", eprService.listAllEventPlanningRequests());
        model.addAttribute("allTasksCompleted", allTaskscompleted);

        return "epr/list";
    }

    @RequestMapping("/{id}")
    public String showEventPlanningRequest(@PathVariable Long id, Model model,  RedirectAttributes redirectAttributes){
        EventPlanningRequest epr = eprService.getEventPlanningRequestById(id);
        if (epr == null) {
            redirectAttributes.addFlashAttribute("error", "Not found!");
            redirectAttributes.addFlashAttribute("message", "The requested event planning request does not exist");
            return "redirect:/epr/list";
        }
        model.addAttribute("epr", eprService.getEventPlanningRequestById(id));
        return "epr/view";
    }


    // XXX Select the current one in view 'epr/form'
//    @RequestMapping("/edit/{id}")
//    public String edit(@PathVariable Long id, Model model){
//        model.addAttribute("epr", eprService.getEventPlanningRequestById(id));
//        model.addAttribute("clients", clientRepository.findAll());
//        return "epr/form";
//    }

    @RequestMapping(value={"/new", "/create"})
    public String newEventPlanningRequest(Model model, @ModelAttribute("epr") EventPlanningRequest epr){
        model.addAttribute("clients", clientRepository.findAll());
        return "epr/form";
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        CustomDateEditor editor = new CustomDateEditor(new SimpleDateFormat("yyyy-MM-dd"), true);
        binder.registerCustomEditor(Date.class, editor);
    }
    @Transactional
    @RequestMapping(value = "", method = RequestMethod.POST)
    public String saveEventPlanningRequest(@Valid @ModelAttribute("epr") EventPlanningRequest eventPlanningRequest,
                                           Errors errors,
                                           Model model,
                                           RedirectAttributes redirectAttributes){
        if (errors.hasErrors()) {
            log.info(errors.getAllErrors().toString());
            redirectAttributes.addFlashAttribute("error", "Invalid input, please try again.");
            redirectAttributes.addFlashAttribute("epr", eventPlanningRequest);
            return "redirect:/epr/create";
        }
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByUsername(username);
            AuditRecord ar = new AuditRecord();
            ar.setModifiedBy(user);
            if (eventPlanningRequest.getId() == null) {
                eventPlanningRequest.setCreator(user);
                ar.setField("Status");
                ar.setNewValue("Created");
            } else {
                ar.setField("Event planning request data");
                ar.setNewValue("Edited");
            }
            auditService.saveAuditRecord(ar);

            eventPlanningRequest.getAuditHistory().add(ar);
            eprService.saveEventPlanningRequest(eventPlanningRequest);

            Client client = eventPlanningRequest.getClient();
            client.addEpr(eventPlanningRequest);
            clientRepository.save(client);

            redirectAttributes.addFlashAttribute("info", "Event planning request successfully created.");
            return "redirect:/epr/" + eventPlanningRequest.getId();
        } catch(Exception ex) {
            log.error("Ex: " + ex.getMessage());
            model.addAttribute("epr", eventPlanningRequest);
            model.addAttribute("error", "Invalid input, please try again.");
            model.addAttribute("message", "Invalid input, please try again.");

            return "redirect:/epr/create";
        }
    }

    @RequestMapping("/cs/approve/{id}")
    public String customServiceApprove(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        EventPlanningRequest epr = eprService.getEventPlanningRequestById(id);
        if (epr.getStatus() == EPRStatus.NEW) {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByUsername(username);
            AuditRecord ar = new AuditRecord();
            ar.setField("Status");
            ar.setNewValue("Approved by customer service");
            ar.setModifiedBy(user);
            auditService.saveAuditRecord(ar);
            epr.addToAuditHistory(ar);

            epr.setStatus(EPRStatus.REVIEWED_BY_CS);
            eprService.saveEventPlanningRequest(epr);
            redirectAttributes.addFlashAttribute("info", "The event planning request was successfully approved.");
            redirectAttributes.addFlashAttribute("epr", epr);
            return "redirect:/epr/" + id;
        } else {
            model.addAttribute("error", "Improper flow");
            model.addAttribute("message", "The selected request cannot be approved.");
            return "/redirect:/epr/list";
        }
    }
    @RequestMapping("/cs/reject/{id}")
    public String customServiceReject(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        EventPlanningRequest epr = eprService.getEventPlanningRequestById(id);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username);
        AuditRecord ar = new AuditRecord();
        ar.setField("Status");
        ar.setNewValue("Rejected by customer service");
        ar.setModifiedBy(user);
        auditService.saveAuditRecord(ar);
        epr.addToAuditHistory(ar);

        epr.setStatus(EPRStatus.REJECTED_CS);
        eprService.saveEventPlanningRequest(epr);
        redirectAttributes.addFlashAttribute("info", "The event planning request was successfully rejected.");
        return "redirect:/epr/list";
    }

    @RequestMapping("/admin/approve/{id}")
    public String adminApprove(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        EventPlanningRequest epr = eprService.getEventPlanningRequestById(id);
        if (epr.getStatus() == EPRStatus.REVIEWED_BY_FINANCE) {

            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByUsername(username);
            AuditRecord ar = new AuditRecord();
            ar.setField("Status");
            ar.setNewValue("Approved by administration manager");
            ar.setModifiedBy(user);
            auditService.saveAuditRecord(ar);
            epr.addToAuditHistory(ar);

            epr.setStatus(EPRStatus.APPROVED);
            eprService.saveEventPlanningRequest(epr);
            redirectAttributes.addFlashAttribute("info", "The event planning request was successfully approved.");
            redirectAttributes.addFlashAttribute("epr", epr);
            return "redirect:/epr/" + id;
        } else {
            redirectAttributes.addFlashAttribute("error", "Improper flow");
            redirectAttributes.addFlashAttribute("message", "The selected request cannot be approved.");
            redirectAttributes.addFlashAttribute("epr", epr);
            return "redirect:/epr/list";
        }
    }
    @RequestMapping("/admin/reject/{id}")
    public String adminReject(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        EventPlanningRequest epr = eprService.getEventPlanningRequestById(id);

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username);
        AuditRecord ar = new AuditRecord();
        ar.setField("Status");
        ar.setNewValue("Rejected by administration manager");
        ar.setModifiedBy(user);
        auditService.saveAuditRecord(ar);
        epr.addToAuditHistory(ar);

        epr.setStatus(EPRStatus.REJECTED_ADMIN);
        eprService.saveEventPlanningRequest(epr);
        redirectAttributes.addFlashAttribute("info", "The event planning request was successfully rejected.");
        return "redirect:/epr/list";
    }

    @RequestMapping("/feedback/{id}")
    public String financialFeedback(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        EventPlanningRequest epr = eprService.getEventPlanningRequestById(id);
        if (epr.getStatus() != EPRStatus.REVIEWED_BY_CS) {
            redirectAttributes.addFlashAttribute("error", "Improper flow");
            redirectAttributes.addFlashAttribute("message", "Financial feedback cannot be provided for the request.");
            return "redirect:/epr/list";
        }
        model.addAttribute("epr", epr);
        return "epr/finance-feedback";
    }
    @RequestMapping(value = "/feedback/{id}", method = RequestMethod.POST)
    public String saveFinancialFeedback(@RequestParam String feedback, @PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        EventPlanningRequest epr = eprService.getEventPlanningRequestById(id);

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username);
        AuditRecord ar = new AuditRecord();
        ar.setField("Status");
        ar.setNewValue("Reviewed by financial manager");
        ar.setModifiedBy(user);
        auditService.saveAuditRecord(ar);
        epr.addToAuditHistory(ar);

        epr.setFinancialFeedback(feedback);
        epr.setStatus(EPRStatus.REVIEWED_BY_FINANCE);
        eprService.saveEventPlanningRequest(epr);
        redirectAttributes.addFlashAttribute("info", "Financial feedback submitted for '" + epr.getName() + "'.");
        return "redirect:/epr/list";
    }

    @RequestMapping("/start/{id}")
    public String startEvent(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        EventPlanningRequest epr = eprService.getEventPlanningRequestById(id);

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username);
        AuditRecord ar = new AuditRecord();
        ar.setField("Status");
        ar.setNewValue("Marked as 'In Progress' by team manager after all tasks completed");
        ar.setModifiedBy(user);
        auditService.saveAuditRecord(ar);
        epr.addToAuditHistory(ar);

        epr.setStatus(EPRStatus.IN_PROGRESS);
        eprService.saveEventPlanningRequest(epr);
        redirectAttributes.addFlashAttribute("info", "The event has been put in progress.");
        return "redirect:/epr/list";
    }

}
