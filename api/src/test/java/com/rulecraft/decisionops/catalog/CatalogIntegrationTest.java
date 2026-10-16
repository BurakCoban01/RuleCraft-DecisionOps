package com.rulecraft.decisionops.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rulecraft.decisionops.catalog.CatalogService.*;
import com.rulecraft.decisionops.catalog.domain.*;
import com.rulecraft.decisionops.identity.domain.TenantRole;
import com.rulecraft.decisionops.identity.domain.UserAccount;
import com.rulecraft.decisionops.identity.persistence.UserAccountRepository;
import com.rulecraft.decisionops.identity.security.RuleCraftPrincipal;
import com.rulecraft.decisionops.platform.web.DomainProblemException;
import com.rulecraft.decisionops.projects.ProjectService;
import com.rulecraft.decisionops.projects.domain.ProjectRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class CatalogIntegrationTest {
    @Autowired CatalogService catalog;
    @Autowired ProjectService projects;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Clock clock;
    @Autowired JdbcClient jdbc;
    RuleCraftPrincipal admin;

    @BeforeEach void actor(){
        admin=new RuleCraftPrincipal(users.findForLogin("acme-rules","admin@example.test").orElseThrow(),"acme-rules");
    }

    @Test
    void persistsReusableTypesAndTypedDecisionService(){
        var project=projects.create(admin,key("P"),"Underwriting decisions",null);
        var risk=catalog.createType(admin,project.id(),enumType(key("RISK"),"Risk band","LOW","HIGH"));
        var applicant=catalog.createType(admin,project.id(),objectType(key("APP"),"Applicant",
                new FieldCommand("age","Age",builtin(BuiltinType.NUMBER),true),
                new FieldCommand("riskBand","Risk band",custom(risk.id()),true)));
        var service=catalog.createService(admin,project.id(),new DecisionServiceCommand(key("ELIG"),"Eligibility",null,
                List.of(new FieldCommand("applicant","Applicant",custom(applicant.id()),true)),
                List.of(new FieldCommand("eligible","Eligible",builtin(BuiltinType.BOOLEAN),true)),null));

        assertThat(catalog.listTypes(admin,project.id())).extracting(TypeView::name).contains("Risk band","Applicant");
        assertThat(service.inputs()).singleElement().satisfies(field->assertThat(field.type().customTypeKey()).isEqualTo(applicant.typeKey()));
        assertThat(service.outputs()).singleElement().satisfies(field->assertThat(field.type().builtinType()).isEqualTo(BuiltinType.BOOLEAN));
        assertThat(jdbc.sql("select count(*) from audit_event where action = 'DECISION_SERVICE_CREATED'").query(Long.class).single()).isPositive();
    }

    @Test
    void rejectsCyclesCrossProjectReferencesAndDeletingUsedTypes(){
        var first=projects.create(admin,key("P"),"First",null);var second=projects.create(admin,key("P"),"Second",null);
        var a=catalog.createType(admin,first.id(),objectType(key("A"),"Type A",new FieldCommand("value","Value",builtin(BuiltinType.TEXT),true)));
        var b=catalog.createType(admin,first.id(),objectType(key("B"),"Type B",new FieldCommand("a","Type A",custom(a.id()),true)));

        assertCode("RC-TYPE-CYCLE",()->catalog.updateType(admin,first.id(),a.id(),new TypeCommand(a.typeKey(),a.name(),null,TypeKind.OBJECT,null,
                List.of(new FieldCommand("b","Type B",custom(b.id()),true)),List.of(),a.version())));
        assertCode("RC-TYPE-REF-NOT-FOUND",()->catalog.createType(admin,second.id(),objectType(key("C"),"Cross project",
                new FieldCommand("a","Type A",custom(a.id()),true))));
        assertCode("RC-TYPE-IN-USE",()->catalog.deleteType(admin,first.id(),a.id(),a.version()));
    }

    @Test
    void viewerCanReadButCannotMutateCatalog(){
        var project=projects.create(admin,key("P"),"Read only catalog",null);
        var type=catalog.createType(admin,project.id(),enumType(key("STATE"),"State","OPEN","CLOSED"));
        var now=Instant.now(clock);
        var account=users.save(new UserAccount(admin.tenantId(),"viewer"+UUID.randomUUID()+"@example.test","Vera Viewer",
                passwordEncoder.encode("CorrectHorseBattery56!"),TenantRole.MEMBER,now));
        projects.upsertMember(admin,project.id(),account.getId(),ProjectRole.VIEWER);
        var viewer=new RuleCraftPrincipal(account,"acme-rules");

        assertThat(catalog.listTypes(viewer,project.id())).extracting(TypeView::id).contains(type.id());
        assertCode("RC-PROJECT-EDIT-FORBIDDEN",()->catalog.createType(viewer,project.id(),enumType(key("NO"),"Denied","A")));
    }

    private TypeCommand objectType(String key,String name,FieldCommand...fields){return new TypeCommand(key,name,null,TypeKind.OBJECT,null,List.of(fields),List.of(),null);}
    private TypeCommand enumType(String key,String name,String...values){return new TypeCommand(key,name,null,TypeKind.ENUM,null,List.of(),java.util.Arrays.stream(values).map(v->new EnumValueCommand(v,v)).toList(),null);}
    private TypeRefCommand builtin(BuiltinType type){return new TypeRefCommand(type,null);}
    private TypeRefCommand custom(UUID id){return new TypeRefCommand(null,id);}
    private String key(String prefix){return prefix+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();}
    private void assertCode(String code,Runnable action){assertThatThrownBy(action::run).isInstanceOf(DomainProblemException.class).extracting("code").isEqualTo(code);}
}
