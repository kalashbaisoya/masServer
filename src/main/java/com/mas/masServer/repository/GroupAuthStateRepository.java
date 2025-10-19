package com.mas.masServer.repository;

import com.mas.masServer.entity.Group;
import com.mas.masServer.entity.GroupAuthState;
import com.mas.masServer.entity.IsOnline;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;

public interface GroupAuthStateRepository extends JpaRepository<GroupAuthState, Long> {
    List<GroupAuthState> findByMembershipGroupGroupIdAndIsOnline(Long groupId, IsOnline isOnline);

    long countByMembershipGroupAndIsOnline(Group group, IsOnline isOnline);

    GroupAuthState findByMembershipMembershipIdAndIsOnline(Long membershipId, IsOnline isOnline);

    List<GroupAuthState> findByMembershipGroup(Group group);
}
