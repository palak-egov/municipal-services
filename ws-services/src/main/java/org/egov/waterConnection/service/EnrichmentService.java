package org.egov.waterConnection.service;


import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.egov.waterConnection.config.WSConfiguration;
import org.egov.waterConnection.constants.WCConstants;
import org.egov.waterConnection.model.AuditDetails;
import org.egov.waterConnection.model.Connection.ApplicationStatusEnum;
import org.egov.waterConnection.model.Property;
import org.egov.waterConnection.model.WaterConnection;
import org.egov.waterConnection.model.WaterConnectionRequest;
import org.egov.waterConnection.model.Idgen.IdResponse;
import org.egov.waterConnection.repository.IdGenRepository;
import org.egov.waterConnection.model.SearchCriteria;
import org.egov.waterConnection.util.WaterServicesUtil;
import org.egov.waterConnection.validator.ValidateProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class EnrichmentService {

	@Autowired
	WaterServicesUtil waterServicesUtil;

	@Autowired
	IdGenRepository idGenRepository;

	@Autowired
	WSConfiguration config;

	@Autowired
	ValidateProperty validateProperty;

	/**
	 * 
	 * @param waterConnectionList List of water connection for enriching the water connection with property.
	 * @param requestInfo is RequestInfo from request
	 */
	public void enrichWaterSearch(List<WaterConnection> waterConnectionList, RequestInfo requestInfo,
			SearchCriteria waterConnectionSearchCriteria) {
		
		if(!waterConnectionList.isEmpty()) {
			String propertyIdsString = waterConnectionList.stream()
					.map(waterConnection -> waterConnection.getProperty().getPropertyId()).collect(Collectors.toList())
					.stream().collect(Collectors.joining(","));
			List<Property> propertyList = waterServicesUtil.searchPropertyOnId(waterConnectionSearchCriteria.getTenantId(),
					propertyIdsString, requestInfo);
			HashMap<String, Property> propertyMap = propertyList.stream().collect(Collectors.toMap(Property::getPropertyId,
					Function.identity(), (oldValue, newValue) -> newValue, LinkedHashMap::new));
			waterConnectionList.forEach(waterConnection -> {
				String propertyId = waterConnection.getProperty().getPropertyId();
				if (propertyMap.containsKey(propertyId)) {
					waterConnection.setProperty(propertyMap.get(propertyId));
				} else {
					throw new CustomException("INVALID SEARCH",
							"NO PROPERTY FOUND FOR " + waterConnection.getConnectionNo() + " WATER CONNECTION No");
				}
			});
		}
	}

	/**
	 * Enrich water connection
	 * 
	 * @param waterConnectionRequest
	 */
	public void enrichWaterConnection(WaterConnectionRequest waterConnectionRequest) {
		validateProperty.enrichPropertyForWaterConnection(waterConnectionRequest);
		AuditDetails auditDetails = waterServicesUtil
				.getAuditDetails(waterConnectionRequest.getRequestInfo().getUserInfo().getUuid(), true);
		waterConnectionRequest.getWaterConnection().setId(UUID.randomUUID().toString());
		waterConnectionRequest.getWaterConnection().setConnectionExecutionDate(Instant.now().getEpochSecond() * 1000);
		setApplicationIdgenIds(waterConnectionRequest);
		setStatusForCreate(waterConnectionRequest);
	}
	
	/**
	 * 
	 * @param waterConnectionRequest
	 * @param MDMS
	 *            Data
	 */
	public void enrichWaterConnectionWithMDMSData(WaterConnectionRequest waterConnectionRequest,
			List<Property> propertyList) {
		if (propertyList != null && !propertyList.isEmpty())
			waterConnectionRequest.getWaterConnection().setProperty(propertyList.get(0));
	}

	/**
	 * Sets the WaterConnectionId for given WaterConnectionRequest
	 *
	 * @param request
	 *            WaterConnectionRequest which is to be created
	 */
	private void setApplicationIdgenIds(WaterConnectionRequest request) {
		RequestInfo requestInfo = request.getRequestInfo();
		String tenantId = request.getRequestInfo().getUserInfo().getTenantId();
		WaterConnection waterConnection = request.getWaterConnection();
		List<String> applicationNumbers = getIdList(requestInfo, tenantId, config.getWaterApplicationIdGenName(),
				config.getWaterApplicationIdGenFormat(), 1);
		ListIterator<String> itr = applicationNumbers.listIterator();
		Map<String, String> errorMap = new HashMap<>();
		if (applicationNumbers.size() != 1) {
			errorMap.put("IDGEN_ERROR",
					"The Id of WaterConnection returned by idgen is not equal to number of WaterConnection");
		}
		if (!errorMap.isEmpty())
			throw new CustomException(errorMap);
		waterConnection.setApplicationNo(itr.next());
	}

	private List<String> getIdList(RequestInfo requestInfo, String tenantId, String idKey, String idformat, int count) {
		List<IdResponse> idResponses = idGenRepository.getId(requestInfo, tenantId, idKey, idformat, count)
				.getIdResponses();

		if (CollectionUtils.isEmpty(idResponses))
			throw new CustomException("IDGEN ERROR", "No ids returned from idgen Service");

		return idResponses.stream().map(IdResponse::getId).collect(Collectors.toList());
	}
	
	 /**
     * Sets status for create request
     * @param WaterConnectionRequest The create request
     */
	private void setStatusForCreate(WaterConnectionRequest waterConnectionRequest) {
		if (waterConnectionRequest.getWaterConnection().getAction().equalsIgnoreCase(WCConstants.ACTION_INITIATE)) {
			waterConnectionRequest.getWaterConnection().setApplicationStatus(ApplicationStatusEnum.INITIATED);
		}
		if (waterConnectionRequest.getWaterConnection().getAction().equalsIgnoreCase(WCConstants.ACTION_APPLY)) {
			waterConnectionRequest.getWaterConnection().setApplicationStatus(ApplicationStatusEnum.APPLIED);
		}
	}
	
	/**
	 * Enrich update water connection
	 * 
	 * @param waterConnectionRequest
	 */
	public void enrichUpdateWaterConnection(WaterConnectionRequest waterConnectionRequest) {
		validateProperty.enrichPropertyForWaterConnection(waterConnectionRequest);
		AuditDetails auditDetails = waterServicesUtil
				.getAuditDetails(waterConnectionRequest.getRequestInfo().getUserInfo().getUuid(), false);
	}
}
