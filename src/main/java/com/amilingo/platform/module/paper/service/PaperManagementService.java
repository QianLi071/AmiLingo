package com.amilingo.platform.module.paper.service;

import com.amilingo.platform.module.paper.dto.PaperManagementDTO.*;
import com.amilingo.platform.module.paper.entity.*;
import com.amilingo.platform.module.paper.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class PaperManagementService {
    private final PaperRepository paperRepo;
    private final PaperAuthorRepository authorRepo;
    private final PublicationVenueRepository venueRepo;
    private final PaperAttachmentRepository attachmentRepo;
    private final PaperCollaboratorRepository collaboratorRepo;
    private final PaperAccessService access;

    @Transactional public PaperAuthor addAuthor(Long paperId,AuthorRequest r){ access.requireEdit(paperId,r.actorUserId());
        if(authorRepo.existsByPaperIdAndAuthorOrder(paperId,r.authorOrder()))throw new IllegalArgumentException("作者顺序已占用");
        return authorRepo.save(toAuthor(paperId,r)); }
    @Transactional public PaperAuthor updateAuthor(Long id,AuthorRequest r){ PaperAuthor a=author(id); access.requireEdit(a.getPaperId(),r.actorUserId());
        if(!a.getAuthorOrder().equals(r.authorOrder())&&authorRepo.existsByPaperIdAndAuthorOrder(a.getPaperId(),r.authorOrder()))throw new IllegalArgumentException("作者顺序已占用");
        a.setUserId(r.userId());a.setName(r.name());a.setInstitution(r.institution());a.setAuthorOrder(r.authorOrder());
        a.setCoFirstAuthor(Boolean.TRUE.equals(r.coFirstAuthor()));a.setCorrespondingAuthor(Boolean.TRUE.equals(r.correspondingAuthor()));a.setContribution(r.contribution());return authorRepo.save(a); }
    @Transactional public void deleteAuthor(Long id,Long actor){PaperAuthor a=author(id);access.requireEdit(a.getPaperId(),actor);authorRepo.delete(a);}
    public List<PaperAuthor> authors(Long paperId,Long actor){access.requireRead(paperId,actor);return authorRepo.findByPaperIdOrderByAuthorOrderAsc(paperId);}

    @Transactional public PublicationVenue createVenue(VenueRequest r){return venueRepo.save(toVenue(new PublicationVenue(),r));}
    @Transactional public PublicationVenue updateVenue(Long id,VenueRequest r){PublicationVenue v=venueRepo.findById(id).orElseThrow(()->new IllegalArgumentException("刊会不存在"));
        if(!v.getOwnerUserId().equals(r.actorUserId()))throw new SecurityException("无权修改该刊会");return venueRepo.save(toVenue(v,r));}
    @Transactional public void deleteVenue(Long id,Long actor){PublicationVenue v=venueRepo.findById(id).orElseThrow(()->new IllegalArgumentException("刊会不存在"));if(!v.getOwnerUserId().equals(actor))throw new SecurityException("无权删除该刊会");venueRepo.delete(v);}
    public List<PublicationVenue> venues(Long actor,String type){return type==null||type.isBlank()?venueRepo.findByOwnerUserIdOrderByNameAsc(actor):venueRepo.findByOwnerUserIdAndTypeOrderByNameAsc(actor,type);}

    @Transactional public PaperAttachment addAttachment(Long paperId,AttachmentRequest r){access.requireEdit(paperId,r.actorUserId());return attachmentRepo.save(PaperAttachment.builder()
        .paperId(paperId).relatedRecordId(r.relatedRecordId()).category(r.category()).fileName(r.fileName()).contentType(r.contentType())
        .fileSize(r.fileSize()).storageKey(r.storageKey()).fileUrl(r.fileUrl()).fileHash(r.fileHash()).versionNo(r.versionNo()).uploadedBy(r.actorUserId()).status("ACTIVE").build());}
    @Transactional public void deleteAttachment(Long id,Long actor){PaperAttachment a=attachmentRepo.findById(id).orElseThrow(()->new IllegalArgumentException("附件不存在"));access.requireEdit(a.getPaperId(),actor);a.setStatus("DELETED");attachmentRepo.save(a);}
    public List<PaperAttachment> attachments(Long paperId,Long actor,String category){access.requireRead(paperId,actor);return category==null||category.isBlank()?attachmentRepo.findByPaperIdAndStatusOrderByCreatedAtDesc(paperId,"ACTIVE"):attachmentRepo.findByPaperIdAndCategoryAndStatusOrderByCreatedAtDesc(paperId,category,"ACTIVE");}

    @Transactional public PaperCollaborator addCollaborator(Long paperId,CollaboratorRequest r){access.requireOwner(paperId,r.actorUserId());
        if(collaboratorRepo.findByPaperIdAndUserId(paperId,r.userId()).isPresent())throw new IllegalArgumentException("协作者已存在");
        return collaboratorRepo.save(PaperCollaborator.builder().paperId(paperId).userId(r.userId()).role(r.role()).status("ACTIVE").invitedBy(r.actorUserId()).build());}
    @Transactional public PaperCollaborator updateCollaborator(Long id,CollaboratorUpdateRequest r){PaperCollaborator c=collaboratorRepo.findById(id).orElseThrow(()->new IllegalArgumentException("协作者不存在"));access.requireOwner(c.getPaperId(),r.actorUserId());if(r.role()!=null)c.setRole(r.role());if(r.status()!=null)c.setStatus(r.status());return collaboratorRepo.save(c);}
    @Transactional public void removeCollaborator(Long id,Long actor){PaperCollaborator c=collaboratorRepo.findById(id).orElseThrow(()->new IllegalArgumentException("协作者不存在"));access.requireOwner(c.getPaperId(),actor);collaboratorRepo.delete(c);}
    public List<PaperCollaborator> collaborators(Long paperId,Long actor){access.requireOwner(paperId,actor);return collaboratorRepo.findByPaperIdOrderByCreatedAtAsc(paperId);}

    public Page<Paper> search(PaperSearchRequest r){
        Set<Long> accessible=new HashSet<>();paperRepo.findByUserIdOrderByCreatedAtDesc(r.actorUserId()).forEach(p->accessible.add(p.getId()));
        collaboratorRepo.findByUserIdAndStatus(r.actorUserId(),"ACTIVE").forEach(c->accessible.add(c.getPaperId()));
        if(accessible.isEmpty())return Page.empty(pageable(r));
        Specification<Paper> spec=(root,q,cb)->{List<Predicate> ps=new ArrayList<>();ps.add(root.get("id").in(accessible));
            if(has(r.keyword()))ps.add(cb.or(cb.like(cb.lower(root.get("title")),like(r.keyword())),cb.like(cb.lower(root.get("doi")),like(r.keyword()))));
            if(has(r.status()))ps.add(cb.equal(root.get("status"),r.status()));if(has(r.level()))ps.add(cb.equal(root.get("level"),r.level()));
            if(has(r.journal()))ps.add(cb.like(cb.lower(root.get("journal")),like(r.journal())));if(r.authorOrder()!=null)ps.add(cb.equal(root.get("authorOrder"),r.authorOrder()));
            if(r.fromDate()!=null)ps.add(cb.greaterThanOrEqualTo(root.get("submissionDate"),r.fromDate()));if(r.toDate()!=null)ps.add(cb.lessThanOrEqualTo(root.get("submissionDate"),r.toDate()));
            if(r.year()!=null)ps.add(cb.equal(cb.function("year",Integer.class,root.get("publicationDate")),r.year()));return cb.and(ps.toArray(Predicate[]::new));};
        return paperRepo.findAll(spec,pageable(r));
    }
    public PaperStatistics statistics(Long actor){List<Paper> papers=new ArrayList<>();int pageNo=0;Page<Paper> batch;
        do{batch=search(new PaperSearchRequest(actor,null,null,null,null,null,null,null,null,pageNo++,100,"createdAt","DESC"));papers.addAll(batch.getContent());}while(batch.hasNext());
        Map<String,Long> status=papers.stream().collect(Collectors.groupingBy(p->safe(p.getStatus()),Collectors.counting()));
        Map<String,Long> level=papers.stream().collect(Collectors.groupingBy(p->safe(p.getLevel()),Collectors.counting()));
        Map<Integer,Long> year=papers.stream().filter(p->p.getPublicationDate()!=null).collect(Collectors.groupingBy(p->p.getPublicationDate().getYear(),Collectors.counting()));
        long submitted=papers.stream().filter(p->p.getSubmissionDate()!=null).count(),accepted=papers.stream().filter(p->p.getAcceptanceDate()!=null).count();
        double avg=papers.stream().filter(p->p.getSubmissionDate()!=null&&p.getAcceptanceDate()!=null).mapToLong(p->ChronoUnit.DAYS.between(p.getSubmissionDate(),p.getAcceptanceDate())).average().orElse(0);
        return new PaperStatistics(papers.size(),status,level,year,submitted==0?0:(accepted*100.0/submitted),avg);}

    private PaperAuthor author(Long id){return authorRepo.findById(id).orElseThrow(()->new IllegalArgumentException("作者不存在"));}
    private PaperAuthor toAuthor(Long p,AuthorRequest r){return PaperAuthor.builder().paperId(p).userId(r.userId()).name(r.name()).institution(r.institution()).authorOrder(r.authorOrder()).coFirstAuthor(r.coFirstAuthor()).correspondingAuthor(r.correspondingAuthor()).contribution(r.contribution()).build();}
    private PublicationVenue toVenue(PublicationVenue v,VenueRequest r){v.setOwnerUserId(r.actorUserId());v.setName(r.name());v.setType(r.type());v.setIssn(r.issn());v.setPublisher(r.publisher());v.setSubjectCategory(r.subjectCategory());v.setSciQuartile(r.sciQuartile());v.setCasQuartile(r.casQuartile());v.setImpactFactor(r.impactFactor());v.setCcfLevel(r.ccfLevel());v.setEiIndexed(r.eiIndexed());v.setCoreJournal(r.coreJournal());v.setSubmissionUrl(r.submissionUrl());v.setTypicalReviewDays(r.typicalReviewDays());v.setPublicationFee(r.publicationFee());v.setCurrency(r.currency());v.setNotes(r.notes());return v;}
    private Pageable pageable(PaperSearchRequest r){int page=r.page()==null?0:r.page(),size=r.size()==null?20:r.size();String sort=r.sortBy()!=null&&Set.of("createdAt","updatedAt","submissionDate","publicationDate","title").contains(r.sortBy())?r.sortBy():"createdAt";Sort.Direction d="ASC".equalsIgnoreCase(r.sortDirection())?Sort.Direction.ASC:Sort.Direction.DESC;return PageRequest.of(page,size,Sort.by(d,sort));}
    private boolean has(String s){return s!=null&&!s.isBlank();}private String like(String s){return "%"+s.toLowerCase()+"%";}private String safe(String s){return has(s)?s:"UNSPECIFIED";}
}
