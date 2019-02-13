package org.hisp.dhis.dxf2.events.relationship;

/*
 * Copyright (c) 2004-2018, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import static org.hisp.dhis.relationship.RelationshipEntity.PROGRAM_INSTANCE;
import static org.hisp.dhis.relationship.RelationshipEntity.PROGRAM_STAGE_INSTANCE;
import static org.hisp.dhis.relationship.RelationshipEntity.TRACKED_ENTITY_INSTANCE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hisp.dhis.api.util.DateUtils;
import org.hisp.dhis.common.IllegalQueryException;
import org.hisp.dhis.commons.collection.ListUtils;
import org.hisp.dhis.dbms.DbmsManager;
import org.hisp.dhis.dxf2.common.ImportOptions;
import org.hisp.dhis.dxf2.events.RelationshipParams;
import org.hisp.dhis.dxf2.events.TrackedEntityInstanceParams;
import org.hisp.dhis.dxf2.events.TrackerAccessManager;
import org.hisp.dhis.dxf2.events.enrollment.Enrollment;
import org.hisp.dhis.dxf2.events.enrollment.EnrollmentService;
import org.hisp.dhis.dxf2.events.event.Event;
import org.hisp.dhis.dxf2.events.event.EventService;
import org.hisp.dhis.dxf2.events.trackedentity.Relationship;
import org.hisp.dhis.dxf2.events.trackedentity.TrackedEntityInstanceService;
import org.hisp.dhis.dxf2.importsummary.ImportConflict;
import org.hisp.dhis.dxf2.importsummary.ImportStatus;
import org.hisp.dhis.dxf2.importsummary.ImportSummaries;
import org.hisp.dhis.dxf2.importsummary.ImportSummary;
import org.hisp.dhis.dxf2.metadata.feedback.ImportReportMode;
import org.hisp.dhis.program.ProgramInstance;
import org.hisp.dhis.program.ProgramStageInstance;
import org.hisp.dhis.query.Query;
import org.hisp.dhis.query.QueryService;
import org.hisp.dhis.query.Restrictions;
import org.hisp.dhis.relationship.RelationshipConstraint;
import org.hisp.dhis.relationship.RelationshipEntity;
import org.hisp.dhis.relationship.RelationshipItem;
import org.hisp.dhis.relationship.RelationshipType;
import org.hisp.dhis.schema.SchemaService;
import org.hisp.dhis.trackedentity.TrackedEntityInstance;
import org.hisp.dhis.user.CurrentUserService;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;

@Transactional
public abstract class AbstractRelationshipService
    implements RelationshipService
{
    @Autowired
    protected DbmsManager dbmsManager;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private QueryService queryService;

    @Autowired
    private TrackerAccessManager trackerAccessManager;

    @Autowired
    private org.hisp.dhis.relationship.RelationshipService relationshipService;

    @Autowired
    private TrackedEntityInstanceService trackedEntityInstanceService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private EventService eventService;

    @Autowired
    private org.hisp.dhis.trackedentity.TrackedEntityInstanceService teiDaoService;

    @Autowired
    private UserService userService;

    private HashMap<String, RelationshipType> relationshipTypeCache = new HashMap<>();

    private HashMap<String, TrackedEntityInstance> trackedEntityInstanceCache = new HashMap<>();

    private HashMap<String, ProgramInstance> programInstanceCache = new HashMap<>();

    private HashMap<String, ProgramStageInstance> programStageInstanceCache = new HashMap<>();

    @Override
    public List<Relationship> getRelationshipsByTrackedEntityInstance(
        TrackedEntityInstance tei, boolean skipAccessValidation )
    {
        User user = currentUserService.getCurrentUser();

        return relationshipService.getRelationshipsByTrackedEntityInstance( tei, skipAccessValidation ).stream()
            .map( mapDaoToDto( user ) ).collect( Collectors.toList() );
    }

    @Override
    public List<Relationship> getRelationshipsByProgramInstance( ProgramInstance pi, boolean skipAccessValidation )
    {
        User user = currentUserService.getCurrentUser();

        return relationshipService.getRelationshipsByProgramInstance( pi, skipAccessValidation ).stream()
            .map( mapDaoToDto( user ) ).collect( Collectors.toList() );
    }

    @Override
    public List<Relationship> getRelationshipsByProgramStageInstance( ProgramStageInstance psi,
        boolean skipAccessValidation )
    {
        User user = currentUserService.getCurrentUser();

        return relationshipService.getRelationshipsByProgramStageInstance( psi, skipAccessValidation ).stream()
            .map( mapDaoToDto( user ) ).collect( Collectors.toList() );
    }

    @Override
    public ImportSummaries processRelationshipList( List<Relationship> relationships, ImportOptions importOptions )
    {
        ImportSummaries importSummaries = new ImportSummaries();
        importOptions = updateImportOptions( importOptions );

        List<Relationship> create = new ArrayList<>();
        List<Relationship> update = new ArrayList<>();
        List<Relationship> delete = new ArrayList<>();

        //TODO: Logic "delete relationships missing in the payload" is missing. Has to be implemented later.

        if ( importOptions.getImportStrategy().isCreate() )
        {
            create.addAll( relationships );
        }
        else if ( importOptions.getImportStrategy().isCreateAndUpdate() )
        {
            for ( Relationship relationship : relationships )
            {
                sortCreatesAndUpdates( relationship, create, update );
            }
        }
        else if ( importOptions.getImportStrategy().isUpdate() )
        {
            update.addAll( relationships );
        }
        else if ( importOptions.getImportStrategy().isDelete() )
        {
            delete.addAll( relationships );
        }
        else if ( importOptions.getImportStrategy().isSync() )
        {
            for ( Relationship relationship : relationships )
            {
                sortCreatesAndUpdates( relationship, create, update );
            }
        }

        importSummaries.addImportSummaries( addRelationships( create, importOptions ) );
        importSummaries.addImportSummaries( updateRelationships( update, importOptions ) );
        importSummaries.addImportSummaries( deleteRelationships( delete, importOptions ) );

        if ( ImportReportMode.ERRORS == importOptions.getReportMode() )
        {
            importSummaries.getImportSummaries().removeIf( is -> is.getConflicts().isEmpty() );
        }

        return importSummaries;
    }

    @Override
    public ImportSummaries addRelationships( List<Relationship> relationships, ImportOptions importOptions )
    {
        List<List<Relationship>> partitions = Lists.partition( relationships, FLUSH_FREQUENCY );
        importOptions = updateImportOptions( importOptions );

        ImportSummaries importSummaries = new ImportSummaries();

        for ( List<Relationship> _relationships : partitions )
        {
            reloadUser( importOptions );
            prepareCaches( _relationships, importOptions.getUser() );

            for ( Relationship relationship : _relationships )
            {
                importSummaries.addImportSummary( addRelationship( relationship, importOptions ) );
            }

            clearSession();
        }

        return importSummaries;
    }

    @Override
    public ImportSummary addRelationship( Relationship relationship, ImportOptions importOptions )
    {
        ImportSummary importSummary = new ImportSummary( relationship.getRelationship() );
        Set<ImportConflict> importConflicts = new HashSet<>();

        importOptions = updateImportOptions( importOptions );

        // Set up cache if not set already
        if ( !cacheExists() )
        {
            prepareCaches( Lists.newArrayList( relationship ), importOptions.getUser() );
        }

        if ( relationshipService.relationshipExists( relationship.getRelationship() ) )
        {
            String message = "Relationship " + relationship.getRelationship() +
                " already exists";
            return new ImportSummary( ImportStatus.ERROR, message )
                .setReference( relationship.getRelationship() )
                .incrementIgnored();
        }

        importConflicts.addAll( checkRelationship( relationship, importOptions ) );

        if ( !importConflicts.isEmpty() )
        {
            importSummary.setConflicts( importConflicts );
            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.getImportCount().incrementIgnored();
            return importSummary;
        }

        org.hisp.dhis.relationship.Relationship daoRelationship = createDAORelationship(
            relationship, importOptions, importSummary );

        if ( daoRelationship == null )
        {
            return importSummary;
        }

        // Check access for both sides
        List<String> errors = trackerAccessManager.canWrite( importOptions.getUser(), daoRelationship );

        if ( !errors.isEmpty() )
        {
            return new ImportSummary( ImportStatus.ERROR, errors.toString() )
                .incrementIgnored();
        }

        relationshipService.addRelationship( daoRelationship );

        importSummary.setReference( daoRelationship.getUid() );
        importSummary.getImportCount().incrementImported();

        return importSummary;
    }

    @Override
    public ImportSummaries updateRelationships( List<Relationship> relationships, ImportOptions importOptions )
    {
        List<List<Relationship>> partitions = Lists.partition( relationships, FLUSH_FREQUENCY );
        importOptions = updateImportOptions( importOptions );
        ImportSummaries importSummaries = new ImportSummaries();

        for ( List<Relationship> _relationships : partitions )
        {
            reloadUser( importOptions );
            prepareCaches( _relationships, importOptions.getUser() );

            for ( Relationship relationship : _relationships )
            {
                importSummaries.addImportSummary( updateRelationship( relationship, importOptions ) );
            }

            clearSession();
        }

        return importSummaries;
    }

    @Override
    public ImportSummary updateRelationship( Relationship relationship, ImportOptions importOptions )
    {
        ImportSummary importSummary = new ImportSummary( relationship.getRelationship() );
        importOptions = updateImportOptions( importOptions );
        Set<ImportConflict> importConflicts = new HashSet<>();

        // Set up cache if not set already
        if ( !cacheExists() )
        {
            prepareCaches( Lists.newArrayList( relationship ), importOptions.getUser() );
        }

        org.hisp.dhis.relationship.Relationship daoRelationship = relationshipService
            .getRelationship( relationship.getRelationship() );

        importConflicts.addAll( checkRelationship( relationship, importOptions ) );

        if ( daoRelationship == null )
        {
            String message = "Relationship '" + relationship.getRelationship() + "' does not exist";
            importConflicts.add( new ImportConflict( "Relationship", message ) );

            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.getImportCount().incrementIgnored();

            importSummary.setConflicts( importConflicts );
            return importSummary;
        }

        List<String> errors = trackerAccessManager.canWrite( importOptions.getUser(), daoRelationship );

        if ( !errors.isEmpty() || !importConflicts.isEmpty() )
        {
            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.getImportCount().incrementIgnored();

            if ( !errors.isEmpty() )
            {
                importSummary.setDescription( errors.toString() );
            }

            importSummary.setConflicts( importConflicts );
            return importSummary;
        }

        org.hisp.dhis.relationship.Relationship _relationship = createDAORelationship( relationship, importOptions,
            importSummary );

        daoRelationship.setRelationshipType( _relationship.getRelationshipType() );
        daoRelationship.setTo( _relationship.getTo() );
        daoRelationship.setFrom( _relationship.getFrom() );

        relationshipService.updateRelationship( daoRelationship );

        importSummary.setReference( daoRelationship.getUid() );
        importSummary.getImportCount().incrementUpdated();

        return importSummary;
    }

    @Override
    public ImportSummary deleteRelationship( String uid )
    {
        return deleteRelationship( uid, null );
    }

    @Override
    public ImportSummaries deleteRelationships( List<Relationship> relationships, ImportOptions importOptions )
    {
        ImportSummaries importSummaries = new ImportSummaries();
        importOptions = updateImportOptions( importOptions );

        int counter = 0;

        for ( Relationship relationship : relationships )
        {
            importSummaries.addImportSummary( deleteRelationship( relationship.getRelationship(), importOptions ) );

            if ( counter % FLUSH_FREQUENCY == 0 )
            {
                clearSession();
            }

            counter++;
        }

        return importSummaries;
    }

    @Override
    public Relationship getRelationshipByUid( String id )
    {
        org.hisp.dhis.relationship.Relationship relationship = relationshipService.getRelationship( id );

        if ( relationship == null)
        {
            return null;
        }

        return getRelationship( relationship, currentUserService.getCurrentUser() );
    }

    @Override
    @Transactional
    public Relationship getRelationship( org.hisp.dhis.relationship.Relationship dao, RelationshipParams params,
        User user )
    {
        List<String> errors = trackerAccessManager.canRead( user, dao );

        if ( !errors.isEmpty() )
        {
            throw new IllegalQueryException( errors.toString() );
        }

        Relationship relationship = new Relationship();

        relationship.setRelationship( dao.getUid() );
        relationship.setRelationshipType( dao.getRelationshipType().getUid() );
        relationship.setRelationshipName( dao.getRelationshipType().getName() );

        relationship.setFrom( includeRelationshipItem( dao.getFrom(), !params.isIncludeFrom() ) );
        relationship.setTo( includeRelationshipItem( dao.getTo(), !params.isIncludeTo() ) );

        relationship.setCreated( DateUtils.getIso8601NoTz( dao.getCreated() ) );
        relationship.setLastUpdated( DateUtils.getIso8601NoTz( dao.getLastUpdated() ) );

        return relationship;

    }

    private Relationship getRelationship( org.hisp.dhis.relationship.Relationship dao, User user )
    {
        return getRelationship( dao, RelationshipParams.TRUE, user );
    }

    private org.hisp.dhis.dxf2.events.trackedentity.RelationshipItem includeRelationshipItem( RelationshipItem dao,
        boolean uidOnly )
    {
        TrackedEntityInstanceParams teiParams = TrackedEntityInstanceParams.FALSE;
        org.hisp.dhis.dxf2.events.trackedentity.RelationshipItem relationshipItem = new org.hisp.dhis.dxf2.events.trackedentity.RelationshipItem();

        if ( dao.getTrackedEntityInstance() != null )
        {
            org.hisp.dhis.dxf2.events.trackedentity.TrackedEntityInstance tei = new org.hisp.dhis.dxf2.events.trackedentity.TrackedEntityInstance();
            String uid = dao.getTrackedEntityInstance().getUid();

            if ( uidOnly )
            {
                tei.clear();
                tei.setTrackedEntityInstance( uid );
            }
            else
            {
                tei = trackedEntityInstanceService
                    .getTrackedEntityInstance( dao.getTrackedEntityInstance(), teiParams );
            }

            relationshipItem.setTrackedEntityInstance( tei );
        }
        else if ( dao.getProgramInstance() != null )
        {
            Enrollment enrollment = new Enrollment();
            String uid = dao.getProgramInstance().getUid();

            if ( uidOnly )
            {

                enrollment.clear();
                enrollment.setEnrollment( uid );
            }
            else
            {
                enrollment = enrollmentService.getEnrollment( dao.getProgramInstance(), teiParams );
            }

            relationshipItem.setEnrollment( enrollment );
        }
        else if ( dao.getProgramStageInstance() != null )
        {
            Event event = new Event();
            String uid = dao.getProgramStageInstance().getUid();

            if ( uidOnly )
            {
                event.clear();
                event.setEvent( uid );
            }
            else
            {
                event = eventService.getEvent( dao.getProgramStageInstance() );
                event.setRelationships( null );
            }

            relationshipItem.setEvent( event );
        }

        return relationshipItem;

    }

    private ImportSummary deleteRelationship( String uid, ImportOptions importOptions )
    {
        ImportSummary importSummary = new ImportSummary();
        importOptions = updateImportOptions( importOptions );

        if ( uid.isEmpty() )
        {
            importSummary.setStatus( ImportStatus.WARNING );
            importSummary.setDescription( "Missing required property 'relationship'" );
            return importSummary.incrementIgnored();
        }

        org.hisp.dhis.relationship.Relationship daoRelationship = relationshipService.getRelationship( uid );

        if ( daoRelationship != null )
        {
            importSummary.setReference( uid );

            List<String> errors = trackerAccessManager.canWrite( importOptions.getUser(), daoRelationship );

            if ( !errors.isEmpty() )
            {
                importSummary.setDescription( errors.toString() );
                importSummary.setStatus( ImportStatus.ERROR );
                importSummary.getImportCount().incrementIgnored();
                return importSummary;
            }

            relationshipService.deleteRelationship( daoRelationship );

            importSummary.setStatus( ImportStatus.SUCCESS );
            importSummary.setDescription( "Deletion of relationship " + uid + " was successful" );
            return importSummary.incrementDeleted();
        }
        else
        {
            importSummary.setStatus( ImportStatus.WARNING );
            importSummary
                .setDescription( "Relationship " + uid + " cannot be deleted as it is not present in the system" );
            return importSummary.incrementIgnored();
        }
    }

    /**
     * Checks the relationship for any conflicts, like missing or invalid references.
     *
     * @param relationship
     * @param importOptions
     * @return
     */
    private List<ImportConflict> checkRelationship( Relationship relationship, ImportOptions importOptions )
    {
        List<ImportConflict> conflicts = new ArrayList<>();

        RelationshipType relationshipType = null;

        if ( StringUtils.isEmpty( relationship.getRelationshipType() ) )
        {
            conflicts
                .add( new ImportConflict( relationship.getRelationship(), "Missing property 'relationshipType'" ) );
        }
        else
        {
            relationshipType = relationshipTypeCache.get( relationship.getRelationshipType() );
        }

        if ( relationship.getFrom() == null || getUidOfRelationshipItem( relationship.getFrom() ).isEmpty() )
        {
            conflicts.add( new ImportConflict( relationship.getRelationship(), "Missing property 'from'" ) );
        }

        if ( relationship.getTo() == null || getUidOfRelationshipItem( relationship.getTo() ).isEmpty() )
        {
            conflicts.add( new ImportConflict( relationship.getRelationship(), "Missing property 'to'" ) );
        }

        if ( relationship.getFrom().equals( relationship.getTo() ) )
        {
            conflicts.add( new ImportConflict( relationship.getRelationship(), "Self-referencing relationships are not allowed." ));
        }

        if ( !conflicts.isEmpty() )
        {
            return conflicts;
        }

        if ( relationshipType == null )
        {
            conflicts.add( new ImportConflict( relationship.getRelationship(),
                "relationshipType '" + relationship.getRelationshipType() + "' not found." ) );
            return conflicts;
        }

        conflicts.addAll(
            getRelationshipConstraintConflicts( relationshipType.getFromConstraint(), relationship.getFrom(),
                relationship.getRelationship() ) );
        conflicts.addAll( getRelationshipConstraintConflicts( relationshipType.getToConstraint(), relationship.getTo(),
            relationship.getRelationship() ) );

        return conflicts;
    }

    /**
     * Finds and returns any conflicts between relationship and relationship type
     *
     * @param constraint       the constraint to check
     * @param relationshipItem the relationshipItem to check
     * @param relationshipUid  the uid of the relationship
     * @return a list of conflicts
     */
    private List<ImportConflict> getRelationshipConstraintConflicts( RelationshipConstraint constraint,
        org.hisp.dhis.dxf2.events.trackedentity.RelationshipItem relationshipItem, String relationshipUid )
    {
        List<ImportConflict> conflicts = new ArrayList<>();
        RelationshipEntity entity = constraint.getRelationshipEntity();
        String itemUid = getUidOfRelationshipItem( relationshipItem );

        if ( TRACKED_ENTITY_INSTANCE.equals( entity ) )
        {
            TrackedEntityInstance tei = trackedEntityInstanceCache.get( itemUid );

            if ( tei == null )
            {
                conflicts.add( new ImportConflict( relationshipUid,
                    "TrackedEntityInstance '" + itemUid + "' not found." ) );
            }
            else if ( !tei.getTrackedEntityType().equals( constraint.getTrackedEntityType() ) )
            {
                conflicts.add( new ImportConflict( relationshipUid,
                    "TrackedEntityInstance '" + itemUid + "' has invalid TrackedEntityType." ) );
            }
        }
        else if ( PROGRAM_INSTANCE.equals( entity ) )
        {
            ProgramInstance pi = programInstanceCache.get( itemUid );

            if ( pi == null )
            {
                conflicts.add( new ImportConflict( relationshipUid,
                    "ProgramInstance '" + itemUid + "' not found." ) );
            }
            else if ( !pi.getProgram().equals( constraint.getProgram() ) )
            {
                conflicts.add( new ImportConflict( relationshipUid,
                    "ProgramInstance '" + itemUid + "' has invalid Program." ) );
            }
        }
        else if ( PROGRAM_STAGE_INSTANCE.equals( entity ) )
        {
            ProgramStageInstance psi = programStageInstanceCache.get( itemUid );

            if ( psi == null )
            {
                conflicts.add( new ImportConflict( relationshipUid,
                    "ProgramStageInstance '" + itemUid + "' not found." ) );
            }
            else
            {
                if ( constraint.getProgram() != null &&
                    !psi.getProgramStage().getProgram().equals( constraint.getProgram() ) )
                {
                    conflicts.add( new ImportConflict( relationshipUid,
                        "ProgramStageInstance '" + itemUid + "' has invalid Program." ) );
                }
                else if ( constraint.getProgramStage() != null &&
                    !psi.getProgramStage().equals( constraint.getProgramStage() ) )
                {
                    conflicts.add( new ImportConflict( relationshipUid,
                        "ProgramStageInstance '" + itemUid + "' has invalid ProgramStage." ) );
                }
            }
        }

        return conflicts;
    }

    private String getUidOfRelationshipItem( org.hisp.dhis.dxf2.events.trackedentity.RelationshipItem relationshipItem )
    {
        if ( relationshipItem.getTrackedEntityInstance() != null )
        {
            return relationshipItem.getTrackedEntityInstance().getTrackedEntityInstance();
        }
        else if ( relationshipItem.getEnrollment() != null )
        {
            return relationshipItem.getEnrollment().getEnrollment();
        }
        else if ( relationshipItem.getEvent() != null )
        {
            return relationshipItem.getEvent().getEvent();
        }

        return "";
    }

    protected org.hisp.dhis.relationship.Relationship createDAORelationship( Relationship relationship,
        ImportOptions importOptions, ImportSummary importSummary )
    {
        RelationshipType relationshipType = relationshipTypeCache.get( relationship.getRelationshipType() );
        org.hisp.dhis.relationship.Relationship daoRelationship = new org.hisp.dhis.relationship.Relationship();
        RelationshipItem fromItem = null;
        RelationshipItem toItem = null;

        daoRelationship.setRelationshipType( relationshipType );

        if ( relationship.getRelationship() != null )
        {
            daoRelationship.setUid( relationship.getRelationship() );
        }

        // FROM
        if ( relationshipType.getFromConstraint().getRelationshipEntity().equals( TRACKED_ENTITY_INSTANCE ) )
        {
            fromItem = new RelationshipItem();
            fromItem.setTrackedEntityInstance(
                trackedEntityInstanceCache.get( getUidOfRelationshipItem( relationship.getFrom() ) ) );
        }
        else if ( relationshipType.getFromConstraint().getRelationshipEntity().equals( PROGRAM_INSTANCE ) )
        {
            fromItem = new RelationshipItem();
            fromItem
                .setProgramInstance( programInstanceCache.get( getUidOfRelationshipItem( relationship.getFrom() ) ) );
        }
        else if ( relationshipType.getFromConstraint().getRelationshipEntity().equals( PROGRAM_STAGE_INSTANCE ) )
        {
            fromItem = new RelationshipItem();
            fromItem.setProgramStageInstance(
                programStageInstanceCache.get( getUidOfRelationshipItem( relationship.getFrom() ) ) );
        }

        // TO
        if ( relationshipType.getToConstraint().getRelationshipEntity().equals( TRACKED_ENTITY_INSTANCE ) )
        {
            toItem = new RelationshipItem();
            toItem.setTrackedEntityInstance(
                trackedEntityInstanceCache.get( getUidOfRelationshipItem( relationship.getTo() ) ) );
        }
        else if ( relationshipType.getToConstraint().getRelationshipEntity().equals( PROGRAM_INSTANCE ) )
        {
            toItem = new RelationshipItem();
            toItem.setProgramInstance( programInstanceCache.get( getUidOfRelationshipItem( relationship.getTo() ) ) );
        }
        else if ( relationshipType.getToConstraint().getRelationshipEntity().equals( PROGRAM_STAGE_INSTANCE ) )
        {
            toItem = new RelationshipItem();
            toItem.setProgramStageInstance(
                programStageInstanceCache.get( getUidOfRelationshipItem( relationship.getTo() ) ) );
        }

        daoRelationship.setFrom( fromItem );
        daoRelationship.setTo( toItem );

        return daoRelationship;
    }

    private boolean cacheExists()
    {
        return !relationshipTypeCache.isEmpty();
    }

    private void prepareCaches( List<Relationship> relationships, User user )
    {
        Map<RelationshipEntity, List<String>> relationshipEntities = new HashMap<>();
        Map<String, List<Relationship>> relationshipTypeMap = relationships.stream()
            .collect( Collectors.groupingBy( Relationship::getRelationshipType ) );

        // Find all the RelationshipTypes first, so we know what the uids refer to
        Query query = Query.from( schemaService.getDynamicSchema( RelationshipType.class ) );
        query.setUser( user );
        query.add( Restrictions.in( "id", relationshipTypeMap.keySet() ) );
        queryService.query( query ).forEach( rt -> relationshipTypeCache.put( rt.getUid(), (RelationshipType) rt ) );

        // Group all uids into their respective RelationshipEntities
        relationshipTypeCache.values().stream().forEach( relationshipType -> {
            List<String> fromUids = relationshipTypeMap.get( relationshipType.getUid() ).stream()
                .map( ( r ) -> getUidOfRelationshipItem( r.getFrom() ) ).collect( Collectors.toList() );

            List<String> toUids = relationshipTypeMap.get( relationshipType.getUid() ).stream()
                .map( ( r ) -> getUidOfRelationshipItem( r.getTo() ) ).collect( Collectors.toList() );

            // Merge existing results with newly found ones.

            relationshipEntities.merge( relationshipType.getFromConstraint().getRelationshipEntity(), fromUids,
                ( old, _new ) -> ListUtils.union( old, _new ) );

            relationshipEntities.merge( relationshipType.getToConstraint().getRelationshipEntity(), toUids,
                ( old, _new ) -> ListUtils.union( old, _new ) );
        } );

        // Find and put all Relationship members in their respective cache
        if ( relationshipEntities.get( TRACKED_ENTITY_INSTANCE ) != null )
        {
            teiDaoService.getTrackedEntityInstancesByUid( relationshipEntities.get( TRACKED_ENTITY_INSTANCE ), user)
                .forEach( tei -> trackedEntityInstanceCache.put( tei.getUid(), tei ) );
        }

        if ( relationshipEntities.get( PROGRAM_INSTANCE ) != null )
        {
            Query piQuery = Query.from( schemaService.getDynamicSchema( ProgramInstance.class ) );
            piQuery.setUser( user );
            piQuery.add( Restrictions.in( "id", relationshipEntities.get( PROGRAM_INSTANCE ) ) );
            queryService.query( piQuery )
                .forEach( pi -> programInstanceCache.put( pi.getUid(), (ProgramInstance) pi ) );
        }

        if ( relationshipEntities.get( PROGRAM_STAGE_INSTANCE ) != null )
        {
            Query psiQuery = Query.from( schemaService.getDynamicSchema( ProgramStageInstance.class ) );
            psiQuery.setUser( user );
            psiQuery.add( Restrictions.in( "id", relationshipEntities.get( PROGRAM_STAGE_INSTANCE ) ) );
            queryService.query( psiQuery )
                .forEach( psi -> programStageInstanceCache.put( psi.getUid(), (ProgramStageInstance) psi ) );
        }
    }

    private void clearSession()
    {
        relationshipTypeCache.clear();
        trackedEntityInstanceCache.clear();
        programInstanceCache.clear();
        programStageInstanceCache.clear();

        dbmsManager.clearSession();
    }

    protected ImportOptions updateImportOptions( ImportOptions importOptions )
    {
        if ( importOptions == null )
        {
            importOptions = new ImportOptions();
        }

        if ( importOptions.getUser() == null )
        {
            importOptions.setUser( currentUserService.getCurrentUser() );
        }

        return importOptions;
    }

    protected void reloadUser( ImportOptions importOptions )
    {
        if ( importOptions == null || importOptions.getUser() == null )
        {
            return;
        }

        importOptions.setUser( userService.getUser( importOptions.getUser().getId() ) );
    }

    private Function<org.hisp.dhis.relationship.Relationship, Relationship> mapDaoToDto( User user )
    {
        return relationship -> getRelationship( relationship, user );
    }

    private void sortCreatesAndUpdates( Relationship relationship, List<Relationship> create,
        List<Relationship> update )
    {
        if ( StringUtils.isEmpty( relationship.getRelationship() ) )
        {
            create.add( relationship );
        }
        else
        {
            if ( !relationshipService.relationshipExists( relationship.getRelationship() ) )
            {
                create.add( relationship );
            }
            else
            {
                update.add( relationship );
            }
        }
    }
}
