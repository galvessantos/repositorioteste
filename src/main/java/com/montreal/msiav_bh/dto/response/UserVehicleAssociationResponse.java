package com.montreal.msiav_bh.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVehicleAssociationResponse {
	
    private Long id;
    private Long userId;
    private Long vehicleId;
    private Long associatedById;
    private LocalDateTime createdAt;
}
