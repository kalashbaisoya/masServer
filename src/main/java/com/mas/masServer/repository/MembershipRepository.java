package com.mas.masServer.repository;

import java.util.List;
// import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mas.masServer.entity.Group;
import com.mas.masServer.entity.Membership;
import com.mas.masServer.entity.User;

public interface MembershipRepository extends JpaRepository<Membership,Long> {

    Membership findByUserAndGroup(User user, Group group);

    long countByGroupAndGroupRoleRoleName(Group group, String roleName);

    Membership findByGroupAndGroupRoleRoleName(Group group, String roleName);

    List<Membership> findByUser(User user);

    void deleteByGroup(Group group);

    boolean existsByUserAndGroup(User user, Group group);

    List<Membership> findByGroup(Group group);
    
}
