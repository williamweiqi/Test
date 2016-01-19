package au.com.parcelpoint.api.integration.v4_1
import au.com.parcelpoint.api.helper.ObjectBuilder
import au.com.parcelpoint.api.helper.TestIsolator
import au.com.parcelpoint.api.web.config.AppProfile
import au.com.parcelpoint.api.web.config.AppTestConfig
import au.com.parcelpoint.api.web.config.security.DevAPIClient
import au.com.parcelpoint.api.web.controllers.v4.*
import au.com.parcelpoint.domain.legacy.agent.*
import au.com.parcelpoint.domain.legacy.carrier.CarrierRepository
import au.com.parcelpoint.domain.legacy.location.AgentLocationEntity
import au.com.parcelpoint.domain.legacy.location.LocationRepository
import au.com.parcelpoint.domain.legacy.parcel.ArticleEntity
import au.com.parcelpoint.domain.legacy.retailer.RetailerRepository
import au.com.parcelpoint.domain.shipment.FulfilmentEntity
import au.com.parcelpoint.domain.shipment.FulfilmentItemEntity
import au.com.parcelpoint.domain.shipment.FulfilmentItemRepository
import au.com.parcelpoint.domain.shipment.FulfilmentRepository
import au.com.parcelpoint.domain.sku.SkuLocationInventoryRepository
import au.com.parcelpoint.domain.sku.SkuRepository
import au.com.parcelpoint.domain.user.AgentUserEntity
import au.com.parcelpoint.domain.user.UserRepository
import au.com.parcelpoint.pojo.*
import au.com.parcelpoint.pojo.exceptions.ClientException
import au.com.parcelpoint.pojo.exceptions.NotFoundException
import au.com.parcelpoint.pojo.v4_1.fulfilment.FulfilmentArticleRequest
import au.com.parcelpoint.pojo.v4_1.fulfilment.FulfilmentArticleResponse
import au.com.parcelpoint.pojo.v4_1.fulfilment.FulfilmentRequest
import au.com.parcelpoint.pojo.v4_1.fulfilment.FulfilmentResponse
import au.com.parcelpoint.pojo.v4_1.order.OrderRequest
import au.com.parcelpoint.services.article.ArticleService
import au.com.parcelpoint.services.exceptions.UnauthorizedOperationException
import au.com.parcelpoint.services.fulfilment.FulfilmentService
import au.com.parcelpoint.services.retailer.RetailerService
import au.com.parcelpoint.test.ObjectMotherUtil
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Lists
import groovy.sql.Sql
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.transaction.AfterTransaction
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.util.CollectionUtils
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindingResult
import spock.lang.Shared
import spock.lang.Specification

import javax.transaction.Transactional

@ContextConfiguration(classes = AppTestConfig.class)
@WebAppConfiguration
@Transactional
@ActiveProfiles(AppProfile.TEST_PROFILE)
public class FulfilmentApiIntegrationSpecification extends Specification implements TestIsolator {

    @Autowired
    OrderApi orderApi

    @Autowired
    au.com.parcelpoint.api.web.controllers.v4_1.OrderApi orderApiV4_1;

    @Autowired
    au.com.parcelpoint.api.web.controllers.v4_1.FulfilmentApi fulfilmentAPI_V4_1

    @Autowired
    au.com.parcelpoint.api.web.controllers.v4.FulfilmentApi fulfilmentAPIV4

    @Autowired
    SkuApi skuApi

    @Autowired
    CategoryApi categoryApi

    @Autowired
    ProductApi productApi

    @Autowired
    InventoryApi inventoryApi

    @Autowired
    RetailerService retailerService

    @Autowired
    ArticleService articleService

    @Autowired
    FulfilmentService fulfilmentService

    @Autowired
    LocationRepository locationRepository

    @Autowired
    AgentRepository agentRepository

    @Autowired
    SkuRepository skuRepository

    @Autowired
    SkuLocationInventoryRepository inventoryRepository

    @Autowired
    AgentOpeningRepository agentOpeningRepository

    @Autowired
    RetailerRepository retailerRepository

    @Autowired
    CarrierRepository carrierRepository;

    @Autowired
    UserRepository userRepository

    @Autowired
    FulfilmentItemRepository fulfilmentItemRepository

    @Autowired
    FulfilmentRepository fulfilmentRepository

    @Autowired
    AgentStorageAreaRepository agentStorageAreaRepository

    @Autowired
    DevAPIClient loggedInUser

    @Shared
    AgentOpeningEntity agentOpeningEntity;

    static Retailer retailer
    static Retailer retailer2

    private final static String RESOURCES_FOLDER = "v4_1/orderFulfilmentAPI"

    def init() {
        ObjectMapper mapper = new ObjectMapper()
        URL retailerResource = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/retailer.json")
        if (retailerRepository.findById(loggedInUser.retailerId) == null) {
            retailer = mapper.readValue(retailerResource, Retailer.class)
            retailer = retailerService.saveRetailer(retailer)
        } else {
            retailer = retailerService.getRetailerById(loggedInUser.retailerId)
        }
        loggedInUser.setRetailerId(retailer.retailerId)

        retailerResource = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/retailer2.json")
        retailer2 = mapper.readValue(retailerResource, Retailer.class)
        retailer2 = retailerService.saveRetailer(retailer2)

        agentOpeningEntity = new AgentOpeningEntity();
        agentOpeningEntity = agentOpeningRepository.save(agentOpeningEntity)

        AgentLocationEntity locationAgent1 = ObjectMotherUtil.getAgentLocationEntity()
        locationAgent1.timeZone = "Australia/Sydney"
        locationAgent1.locationReference = "locationRef001"

        locationRepository.save(locationAgent1)

        AgentEntity agent1 = ObjectMotherUtil.getAgentEntity()
        agent1.primaryLocation = locationAgent1
        agent1.externalId = "locationRef001"
        agent1.type = AgentEntity.Types.STORE
        agent1.defaultCarrier = "AUSTRALIA_POST"
        agent1.openingHours = agentOpeningEntity
        agentRepository.save(agent1)

        AgentLocationEntity locationWarehouse = ObjectMotherUtil.getAgentLocationEntity()
        locationWarehouse.timeZone = "Australia/Sydney"
        locationWarehouse.locationReference = "Warehouse1"
        locationRepository.save(locationWarehouse)

        AgentEntity agentWarehouse = ObjectMotherUtil.getAgentEntity()
        agentWarehouse.primaryLocation = locationWarehouse
        agentWarehouse.externalId = "Warehouse1"
        agentWarehouse.type = AgentEntity.Types.WAREHOUSE
        agentWarehouse.defaultCarrier = "AUSTRALIA_POST"
        agentWarehouse.openingHours = agentOpeningEntity
        agentRepository.save(agentWarehouse)


        AgentLocationEntity agentLocation146 = ObjectMotherUtil.getAgentLocationEntity()
        agentLocation146.timeZone = "Australia/Sydney"
        agentLocation146.locationReference = "146"
        locationRepository.save(agentLocation146)

        AgentEntity agent146 = ObjectMotherUtil.getAgentEntity()
        agent146.primaryLocation = agentLocation146
        agent146.externalId = "146"
        agent146.type = AgentEntity.Types.STORE
        agent146.defaultCarrier = "AUSTRALIA_POST"
        agent146.openingHours = agentOpeningEntity
        agentRepository.save(agent146)

        AgentLocationEntity agentLocation045 = ObjectMotherUtil.getAgentLocationEntity()
        agentLocation045.timeZone = "Australia/Sydney"
        agentLocation045.locationReference = "045"
        locationRepository.save(agentLocation045)

        AgentEntity agent045 = ObjectMotherUtil.getAgentEntity()
        agent045.primaryLocation = agentLocation045
        agent045.externalId = "045"
        agent045.type = AgentEntity.Types.STORE
        agent045.defaultCarrier = "AUSTRALIA_POST"
        agent045.openingHours = agentOpeningEntity
        agentRepository.save(agent045)

        AgentStorageAreaEntity agentStorageArea = ObjectMotherUtil.getAgentStorageAreaEntity()
        agentStorageArea.agent = agent045
        agentStorageAreaRepository.save(agentStorageArea)
        agent045.agentStorageAreas = Lists.newArrayList(agentStorageArea)

        // associated agent with the retailer
        AgentUserEntity agentUserWarehouse = userRepository.save(new AgentUserEntity(agent: agentWarehouse, retailer: retailerService.retrieveById(retailer.retailerId)))
        AgentUserEntity agentUserLocation146 = userRepository.save(new AgentUserEntity(agent: agent146, retailer: retailerService.retrieveById(retailer.retailerId)))
        AgentUserEntity agentUser1 = userRepository.save(new AgentUserEntity(agent: agent1, retailer: retailerService.retrieveById(retailer.retailerId)))

        ObjectBuilder persistentObjectBuilder = new ObjectBuilder()
        Category category = persistentObjectBuilder.createNewCategory(categoryApi, "${RESOURCES_FOLDER}/category.json")
        Product product = persistentObjectBuilder.createNewProduct(category, productApi, "${RESOURCES_FOLDER}/product.json")

        Sku sku1 = persistentObjectBuilder.createNewSku(category, product, skuApi, "${RESOURCES_FOLDER}/sku.json")
        Inventory inventory1 = persistentObjectBuilder.createNewInventory(agentWarehouse, inventoryApi, "${RESOURCES_FOLDER}/inventory3.json")

        Sku sku2 = persistentObjectBuilder.createNewSku(category, product, skuApi, "${RESOURCES_FOLDER}/sku2.json")
        Inventory inventory2 = persistentObjectBuilder.createNewInventory(agentWarehouse, inventoryApi, "${RESOURCES_FOLDER}/inventory5.json")

        loggedInUser.setRetailerId(retailer2.retailerId)

        carrierRepository.save(ObjectMotherUtil.getCarrierEntity())

        carrierRepository.save(ObjectMotherUtil.getTemandoCarrierEntity())
    }

    @AfterTransaction
    def reset() {
        loggedInUser.setRetailerId(1)
    }

    def "Book Order and create a fulfilment should be successful"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse response = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse successfulSaveFulfilment = fulfilmentAPI_V4_1.createOrderFulfilment(response.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        then:
        successfulSaveFulfilment != null
        where:
        fileName                          | value   | fulfilment1FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json"
    }

    def "Book Order and create a fulfilment with not created sku should be throw NotFoundException"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId
        fulfilmentRequest.items[0].skuRef = "INVALID SKU"
        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse response = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse successfulSaveFulfilment = fulfilmentAPI_V4_1.createOrderFulfilment(response.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        then:
        successfulSaveFulfilment == null
        thrown(NotFoundException)
        where:
        fileName                          | value   | fulfilment1FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json"
    }


    def "Book Order and create/retrieve fulfilment should be successful"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)

        when:
        FulfilmentResponse fulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)
        then:

        fulfilmentResponse != null

        if (fulfilmentResponse.consignment != null) {
            fulfilmentResponse.consignment.consignmentRef == fulfilmentRequest.consignment.consignmentRef
            fulfilmentResponse.consignment.labelUrl == fulfilmentRequest.consignment.labelUrl
            fulfilmentResponse.consignment.carrierId == fulfilmentRequest.consignment.carrierId
            fulfilmentResponse.consignment.retailerId == fulfilmentRequest.consignment.retailerId
            fulfilmentResponse.consignment.status == fulfilmentRequest.consignment.status
        }

        fulfilmentResponse.fulfilmentType == fulfilmentRequest.fulfilmentType
        fulfilmentResponse.fulfilmentRef == fulfilmentRequest.fulfilmentRef
        fulfilmentResponse.deliveryType == fulfilmentRequest.deliveryType
        fulfilmentResponse.fromAddress.locationRef == fulfilmentRequest.fromAddress.locationRef
        fulfilmentResponse.status == FulfilmentStatus.CREATED

        where:
        fileName                          | value   | fulfilment1FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json"
    }

    def "Book Order and create fulfilment when order does not exist,  should throw not found exception"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId


        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(2, fulfilmentRequest, fulfilmentRequestBindingResult)
        then:
        thrown(NotFoundException)
        where:
        fileName                          | value   | fulfilment1FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json"
    }


    def "Book Order, Create fulfilment and retrieve fulfilment that do not belong to order should throw an UnauthorizedOperationException"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL order1Resource = getClass().getClassLoader().getResource(order1FileName)
        URL order2Resource = getClass().getClassLoader().getResource(order2FileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)

        given:
        Order orderDTO1 = mapper.readValue(order1Resource, Order.class)
        Order orderDTO2 = mapper.readValue(order2Resource, Order.class)

        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)

        BindingResult order1BindingResult = new BeanPropertyBindingResult(orderDTO1, "order")
        BindingResult order2BindingResult = new BeanPropertyBindingResult(orderDTO2, "order")

        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO1.retailer.retailerId = retailer.retailerId
        orderDTO2.retailer.retailerId = retailer.retailerId

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse order1Response = orderApi.bookNewOrder(orderDTO1, order1BindingResult)
        SuccessResponse order2Response = orderApi.bookNewOrder(orderDTO2, order2BindingResult)

        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(order1Response.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        FulfilmentResponse fulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(order2Response.id, orderFulfilmentResponse.id)

        then:
        fulfilmentResponse == null
        thrown(UnauthorizedOperationException)
        where:
        order1FileName                    | value   | fulfilment1FileName                    | order2FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/order2.json"
    }

    def "Book Order, create/update fulfilment should be successful"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)
        URL fulfilmentRequest2Resource = getClass().getClassLoader().getResource(fulfilment2FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        FulfilmentRequest fulfilmentRequest2 = mapper.readValue(fulfilmentRequest2Resource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId


        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        FulfilmentResponse fulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)

        SuccessResponse updateFulfilmentResponse = fulfilmentAPI_V4_1.updateOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id, fulfilmentRequest2, true,
                fulfilmentRequestBindingResult)
        FulfilmentResponse updatedFulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)
        then:

        fulfilmentResponse != null

        if(fulfilmentResponse.consignment != null) {
            fulfilmentResponse.consignment.consignmentRef == fulfilmentRequest.consignment.consignmentRef
            fulfilmentResponse.consignment.labelUrl == fulfilmentRequest.consignment.labelUrl
            fulfilmentResponse.consignment.carrierId == fulfilmentRequest.consignment.carrierId
            fulfilmentResponse.consignment.retailerId == fulfilmentRequest.consignment.retailerId
            fulfilmentResponse.consignment.status == fulfilmentRequest.consignment.status
        }

        fulfilmentResponse.status == FulfilmentStatus.CREATED
        fulfilmentResponse.fulfilmentType == fulfilmentRequest.fulfilmentType
        fulfilmentResponse.fulfilmentRef == fulfilmentRequest.fulfilmentRef
        fulfilmentResponse.deliveryType == fulfilmentRequest.deliveryType
        fulfilmentResponse.fromAddress.locationRef == fulfilmentRequest.fromAddress.locationRef

        // check before update status
        List items = new ArrayList<>(fulfilmentResponse.items);
        items.size() == 1;

        // check after update status
        updateFulfilmentResponse != null

        List updatedItems = new ArrayList<>(updatedFulfilmentResponse.items);
        updatedItems.size() == 1;
        if(updatedFulfilmentResponse.consignment != null) {
            updatedFulfilmentResponse.consignment.consignmentRef != fulfilmentRequest.consignment.consignmentRef
            updatedFulfilmentResponse.consignment.labelUrl != fulfilmentRequest.consignment.labelUrl
            updatedFulfilmentResponse.consignment.status == fulfilmentRequest.consignment.status
        }

        where:
        fileName                          | value   | fulfilment1FileName                    | fulfilment2FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/successfulUpdate.json"
    }


    def "Book Order, create/update fulfilment should throw notfound exception"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)
        URL fulfilmentRequest2Resource = getClass().getClassLoader().getResource(fulfilment2FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        FulfilmentRequest fulfilmentRequest2 = mapper.readValue(fulfilmentRequest2Resource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId


        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        FulfilmentResponse fulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)

        SuccessResponse updateFulfilmentResponse = fulfilmentAPI_V4_1.updateOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id, fulfilmentRequest2, true,
                fulfilmentRequestBindingResult)
        FulfilmentResponse updatedFulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)
        then:
        thrown(NotFoundException)

        where:
        fileName                          | value   | fulfilment1FileName                    | fulfilment2FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/fulfilment-notfound-sku.json"
    }


    def "Book Order, create/update fulfilment when order does not exist,  should throw not found exception"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)
        URL fulfilmentRequest2Resource = getClass().getClassLoader().getResource(fulfilment2FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        FulfilmentRequest fulfilmentRequest2 = mapper.readValue(fulfilmentRequest2Resource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId


        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        FulfilmentResponse fulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)

        SuccessResponse updateFulfilmentResponse = fulfilmentAPI_V4_1.updateOrderFulfilment(2, orderFulfilmentResponse.id, fulfilmentRequest2, true, fulfilmentRequestBindingResult)
        FulfilmentResponse updatedFulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)

        then:
        thrown(NotFoundException)

        where:
        fileName                          | value   | fulfilment1FileName                    | fulfilment2FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/fulfilment1-5.json"
    }

    def "Book Order, create/update fulfilment that do not belong to order should throw an UnauthorizedOperationException"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL order2Resource = getClass().getClassLoader().getResource(fileName2)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)
        URL fulfilmentRequest2Resource = getClass().getClassLoader().getResource(fulfilment2FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        Order order2DTO = mapper.readValue(order2Resource, Order.class)

        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        FulfilmentRequest fulfilmentRequest2 = mapper.readValue(fulfilmentRequest2Resource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId


        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse order2Response = orderApi.bookNewOrder(order2DTO, orderBindingResult)
        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        FulfilmentResponse fulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)

        SuccessResponse updateFulfilmentResponse = fulfilmentAPI_V4_1.updateOrderFulfilment(order2Response.id, orderFulfilmentResponse.id, fulfilmentRequest2, true,
                fulfilmentRequestBindingResult)
        FulfilmentResponse updatedFulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(order2Response.id, orderFulfilmentResponse.id)

        then:
        updatedFulfilmentResponse == null
        thrown(UnauthorizedOperationException)

        where:
        fileName                          | value   | fulfilment1FileName                    | fulfilment2FileName                      | fileName2
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/fulfilment1-5.json" | "${RESOURCES_FOLDER}/order2.json"
    }

    def "Book Order, create/update fulfilment retailer should throw an UnauthorizedOperationException"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)

        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)
        URL fulfilmentRequest2Resource = getClass().getClassLoader().getResource(fulfilment2FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)

        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        FulfilmentRequest fulfilmentRequest2 = mapper.readValue(fulfilmentRequest2Resource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId


        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        fulfilmentRequest2.consignment.retailerId = 2

        SuccessResponse updateFulfilmentResponse = fulfilmentAPI_V4_1.updateOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id, fulfilmentRequest2, true,
                fulfilmentRequestBindingResult)
        then:
        updateFulfilmentResponse == null
        thrown(UnauthorizedOperationException)

        where:
        fileName                          | value   | fulfilment1FileName                    | fulfilment2FileName                      | fileName2
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/fulfilment1-5.json" | "${RESOURCES_FOLDER}/order2.json"
    }


    def "Book Order, create/update fulfilment should be successful and the rejectedQty is zero"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)
        URL fulfilmentRequest2Resource = getClass().getClassLoader().getResource(fulfilment2FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        FulfilmentRequest fulfilmentRequest2 = mapper.readValue(fulfilmentRequest2Resource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId


        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        FulfilmentResponse fulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)

        SuccessResponse updateFulfilmentResponse = fulfilmentAPI_V4_1.updateOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id, fulfilmentRequest2, true,
                fulfilmentRequestBindingResult)
        FulfilmentResponse updatedFulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)

        // get the fulfilment items
        def existingFulfilment = fulfilmentRepository.findOne(updatedFulfilmentResponse.getFulfilmentId())

        List<FulfilmentItemEntity> fulfilmentItemEntity = fulfilmentItemRepository.findByFulfilment(existingFulfilment)

        then:
        fulfilmentResponse != null
        if(fulfilmentResponse.consignment != null) {
            fulfilmentResponse.consignment.consignmentRef == fulfilmentRequest.consignment.consignmentRef
            fulfilmentResponse.consignment.labelUrl == fulfilmentRequest.consignment.labelUrl
            fulfilmentResponse.consignment.carrierId == fulfilmentRequest.consignment.carrierId
            fulfilmentResponse.consignment.retailerId == fulfilmentRequest.consignment.retailerId
            fulfilmentResponse.consignment.status == fulfilmentRequest.consignment.status
        }

        fulfilmentResponse.fulfilmentType == fulfilmentRequest.fulfilmentType
        fulfilmentResponse.fulfilmentRef == fulfilmentRequest.fulfilmentRef
        fulfilmentResponse.deliveryType == fulfilmentRequest.deliveryType
        fulfilmentResponse.fromAddress.locationRef == fulfilmentRequest.fromAddress.locationRef

        // check before update status
        List items = new ArrayList<>(fulfilmentResponse.items);
        items.size() == 1;

        // check after update status
        updateFulfilmentResponse != null;
        List<FulfilmentItem> updatedItems = new ArrayList<>(updatedFulfilmentResponse.items);
        updatedItems.size() == 1;
        if(updatedFulfilmentResponse.consignment != null) {
            updatedFulfilmentResponse.consignment.consignmentRef != fulfilmentRequest.consignment.consignmentRef
            updatedFulfilmentResponse.consignment.labelUrl != fulfilmentRequest.consignment.labelUrl
            updatedFulfilmentResponse.consignment.status == fulfilmentRequest.consignment.status
        }

        fulfilmentItemEntity.size() == 1
        fulfilmentItemEntity.get(0).filledQuantity == updatedItems.get(0).confirmedQty
        fulfilmentItemEntity.get(0).getRejectedQuantity() == 0

        where:
        fileName                          | value   | fulfilment1FileName                    | fulfilment2FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/successfulUpdate.json"
    }


    def "Book Order, create/update fulfilment should be successful and the rejectedQty is 1"() {
        loggedInUser.setRetailerId(270)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)
        URL fulfilmentRequest2Resource = getClass().getClassLoader().getResource(fulfilment2FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        orderDTO.getRetailer().setRetailerId(270L)

        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        FulfilmentRequest updateRequest = mapper.readValue(fulfilmentRequest2Resource, FulfilmentRequest.class)
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        updateRequest.items[0].availableQty = 1

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")

        orderDTO.retailer.retailerId = retailer.retailerId

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        FulfilmentResponse fulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)

        SuccessResponse updateFulfilmentResponse = fulfilmentAPI_V4_1.updateOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id, updateRequest, true,
                fulfilmentRequestBindingResult)
        FulfilmentResponse updatedFulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderResponse.id, orderFulfilmentResponse.id)

        // get the fulfilment items
        def existingFulfilment = fulfilmentRepository.findOne(updatedFulfilmentResponse.getFulfilmentId())

        List<FulfilmentItemEntity> fulfilmentItemEntity = fulfilmentItemRepository.findByFulfilment(existingFulfilment)

        //CustomerShippingConfirmation

        then:

        fulfilmentResponse != null
        if(fulfilmentResponse.consignment != null) {
            fulfilmentResponse.consignment.consignmentRef == fulfilmentRequest.consignment.consignmentRef
            fulfilmentResponse.consignment.labelUrl == fulfilmentRequest.consignment.labelUrl
            fulfilmentResponse.consignment.carrierId == fulfilmentRequest.consignment.carrierId
            fulfilmentResponse.consignment.retailerId == fulfilmentRequest.consignment.retailerId
            fulfilmentResponse.consignment.status == fulfilmentRequest.consignment.status
        }

        fulfilmentResponse.fulfilmentType == fulfilmentRequest.fulfilmentType
        fulfilmentResponse.fulfilmentRef == fulfilmentRequest.fulfilmentRef
        fulfilmentResponse.deliveryType == fulfilmentRequest.deliveryType
        fulfilmentResponse.fromAddress.locationRef == fulfilmentRequest.fromAddress.locationRef

        // check before update status
        List items = new ArrayList<>(fulfilmentResponse.items);
        items.size() == 1;

        // check after update status
        updateFulfilmentResponse != null;
        List<FulfilmentItem> updatedItems = new ArrayList<>(updatedFulfilmentResponse.items);
        updatedItems.size() == 1;
        if(updatedFulfilmentResponse.consignment != null) {
            updatedFulfilmentResponse.consignment.consignmentRef != fulfilmentRequest.consignment.consignmentRef
            updatedFulfilmentResponse.consignment.labelUrl != fulfilmentRequest.consignment.labelUrl
            updatedFulfilmentResponse.consignment.status == fulfilmentRequest.consignment.status
        }
        fulfilmentItemEntity.size() == 1
        fulfilmentItemEntity.get(0).filledQuantity == updatedItems.get(0).confirmedQty
        fulfilmentItemEntity.get(0).getRejectedQuantity() == 1

        where:
        fileName                          | value   | fulfilment1FileName                    | fulfilment2FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/successfulUpdate.json"
    }

    def "Book Order, create/confirm and bookCourier fulfilment should be successful"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId


        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)

        SuccessResponse confirmFulfilmentResponse = fulfilmentAPI_V4_1.confirmFulfilment(orderFulfilmentResponse.id)
        SuccessResponse bookCourierFulfilmentResponse = fulfilmentAPIV4.bookCourier(orderFulfilmentResponse.id)
        then:

        confirmFulfilmentResponse != null
        bookCourierFulfilmentResponse != null

        where:
        fileName                          | value   | fulfilment1FileName                    | fulfilment2FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/successfulUpdate.json"
    }


    def "Book a new order and DC should fulfil, should return 1 fulfilment"() {
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL resource = getClass().getClassLoader().getResource(fileName)
        loggedInUser.setRetailerId(retailer.retailerId)

        given:
        OrderRequest orderDTO = mapper.readValue(resource, OrderRequest.class)
        println orderDTO
        orderDTO.retailerId = loggedInUser.determineRetailerIdForAuthenticatedUser();
        BindingResult bindingResult = new BeanPropertyBindingResult(orderDTO, "order")

        when:
        SuccessResponse response = orderApiV4_1.bookNewOrder(orderDTO, bindingResult)
        def order = orderApiV4_1.retrieveOrder(response.getId())
        FulfilmentResponse fulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(response.id, Integer.valueOf(order.fulfilments.get(0).fulfilmentId));

        FulfilmentEntity fulfilmentFromDatabase = fulfilmentRepository.findOne(Long.valueOf(fulfilmentResponse.fulfilmentId))
        then:
        println response.id
        response.id > 0
        fulfilmentResponse.consignment == null
        fulfilmentResponse.deliveryType == DeliveryType.STANDARD
        fulfilmentFromDatabase.status == au.com.parcelpoint.domain.shipment.FulfilmentStatus.CREATED
        where:
        fileName                                      | value
        "${RESOURCES_FOLDER}/orderV4API_HD_Sku1.json" | "value"
    }


    def "Book a new order and DC should fulfil, should return 1 fulfilment and we reject the fulfilment"() {
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL rejectFulfilmentResource = getClass().getClassLoader().getResource(rejectFulfilmentFileName)
        loggedInUser.setRetailerId(retailer.retailerId)

        given:
        OrderRequest orderDTO = mapper.readValue(orderResource, OrderRequest.class)
        FulfilmentRequest fulfilmentRequestDTO = mapper.readValue(rejectFulfilmentResource, FulfilmentRequest.class)

        println orderDTO
        orderDTO.retailerId = loggedInUser.determineRetailerIdForAuthenticatedUser();
        BindingResult bindingResultOrder = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult bindingResultFulfilment = new BeanPropertyBindingResult(fulfilmentRequestDTO, "Fulfilment")

        when:
        SuccessResponse orderSuccessResponse = orderApiV4_1.bookNewOrder(orderDTO, bindingResultOrder)
        def order = orderApiV4_1.retrieveOrder(orderSuccessResponse.getId())
        Long theFulfilmentId = Long.valueOf(order.fulfilments.get(0).fulfilmentId);
        def fulfilment = fulfilmentAPI_V4_1.updateOrderFulfilment(Long.valueOf(orderSuccessResponse.id), theFulfilmentId,
                fulfilmentRequestDTO, true, bindingResultFulfilment)

        FulfilmentResponse fulfilmentResponseRejected = fulfilmentAPI_V4_1.retrieveOrderFulfilment(orderSuccessResponse.id, theFulfilmentId.intValue());

        FulfilmentEntity fulfilmentFromDatabase = fulfilmentRepository.findOne(Long.valueOf(fulfilmentResponseRejected.fulfilmentId))
        then:
        println orderSuccessResponse.id
        orderSuccessResponse.id > 0
        fulfilment.id > 0
        fulfilmentFromDatabase.status == au.com.parcelpoint.domain.shipment.FulfilmentStatus.REJECTED
        fulfilmentResponseRejected.consignment == null
        where:
        fileName                                 | value   | rejectFulfilmentFileName
        "${RESOURCES_FOLDER}/orderV4API_HD_Sku1.json" | "value" |  "${RESOURCES_FOLDER}/rejectFulfilmentSku1.json"
    }

    def "When an order has a fulfilment with one & only HD_PFDC fulfilment, statuses should updated as fulfilment -> COMPLETE, article -> COLLECTED, order -> COMPLETE when fulfilment is fulfilled"() {

        init()
        ObjectMapper mapper = new ObjectMapper()
        URL resource = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/orderV4API_HD_Sku1.json")
        URL updateFulfilmentPartiallyFulfilled = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/HDFCsuccessfulUpdate.json")

        loggedInUser.setRetailerId(retailer.retailerId)

        given:
        OrderRequest orderDTO = mapper.readValue(resource, OrderRequest.class)
        orderDTO.retailerId = loggedInUser.determineRetailerIdForAuthenticatedUser();
        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        FulfilmentRequest fulfilmentRequestDTO = mapper.readValue(updateFulfilmentPartiallyFulfilled, FulfilmentRequest.class)
        BindingResult fulfilmentBindingResult = new BeanPropertyBindingResult(fulfilmentRequestDTO, "fulfilment")

        when:
        SuccessResponse response = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        def order = orderApiV4_1.retrieveOrder(response.getId())
        SuccessResponse fulfilmentSuccessfulUpdate = fulfilmentAPI_V4_1.updateOrderFulfilment(
                Long.valueOf(response.id), Long.valueOf(order.fulfilments[0].fulfilmentId), fulfilmentRequestDTO, true, fulfilmentBindingResult)

        FulfilmentResponse fulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(response.id, fulfilmentSuccessfulUpdate.id);
        FulfilmentEntity existingFulfilment = fulfilmentRepository.findOne(fulfilmentResponse.getFulfilmentId())

        then:
        response.id == fulfilmentSuccessfulUpdate.id
        fulfilmentResponse.deliveryType == DeliveryType.EXPRESS
        existingFulfilment.order.status == au.com.parcelpoint.domain.order.OrderStatus.COMPLETE
        existingFulfilment.status == au.com.parcelpoint.domain.shipment.FulfilmentStatus.COMPLETE
        fulfilmentResponse.fulfilmentType == FulfilmentType.HD_PFDC
        existingFulfilment.getArticles().size() == 1
        existingFulfilment.getArticles().iterator().next().status == ArticleEntity.State.COLLECTED

    }

    def "When order has more than one fulfilment and one of them is HD_PFDC, order should not be complete even if HD_PFDC fulfilment is confirmed"(){

        init()
        ObjectMapper mapper = new ObjectMapper()
        URL resource = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/orderV4API_HD_Sku1.json")
        URL updateFulfilmentPartiallyFulfilled = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/HDFCsuccessfulUpdate.json")

        loggedInUser.setRetailerId(retailer.retailerId)

        given:
        OrderRequest orderDTO = mapper.readValue(resource, OrderRequest.class)
        orderDTO.retailerId = loggedInUser.determineRetailerIdForAuthenticatedUser();
        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        FulfilmentRequest fulfilmentRequestDTO = mapper.readValue(updateFulfilmentPartiallyFulfilled, FulfilmentRequest.class)
        FulfilmentRequest newfulfilmentRequestDTO = mapper.readValue(updateFulfilmentPartiallyFulfilled, FulfilmentRequest.class)
        BindingResult fulfilmentBindingResult = new BeanPropertyBindingResult(fulfilmentRequestDTO, "fulfilment")
        BindingResult newfulfilmentBindingResult = new BeanPropertyBindingResult(newfulfilmentRequestDTO, "fulfilment")

        when:
        SuccessResponse response = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        def order = orderApiV4_1.retrieveOrder(response.getId())

        newfulfilmentRequestDTO.setFulfilmentType(FulfilmentType.HD_PFS)
        newfulfilmentRequestDTO.setFulfilmentRef("EWN_FULFILMENT_REF")
        newfulfilmentRequestDTO.setDeliveryType(DeliveryType.STANDARD)

        order = orderApiV4_1.retrieveOrder(response.getId())
        FulfilmentEntity firstFulfilment = fulfilmentRepository.findOne(Long.valueOf(order.fulfilments.get(0).fulfilmentId))

        SuccessResponse<Long> responseOfSecondFulfilment = fulfilmentAPI_V4_1.createOrderFulfilment(Long.valueOf(order.orderId),
                newfulfilmentRequestDTO, newfulfilmentBindingResult)

        order = orderApiV4_1.retrieveOrder(response.getId())

        SuccessResponse fulfilmentSuccessfulUpdate = fulfilmentAPI_V4_1.updateOrderFulfilment(
                Long.valueOf(response.id), Long.valueOf(firstFulfilment.id), fulfilmentRequestDTO, true, fulfilmentBindingResult)

        FulfilmentResponse responseOfFirstFulfilment = fulfilmentAPI_V4_1.retrieveOrderFulfilment(response.id, fulfilmentSuccessfulUpdate.id);

        firstFulfilment = fulfilmentRepository.findOne(responseOfFirstFulfilment.getFulfilmentId())
        FulfilmentEntity secondFulfilment = fulfilmentRepository.findOne(responseOfSecondFulfilment.id)

        then:
        response.id  == order.getOrderId().toLong()
        order.fulfilments.size() == 2
        firstFulfilment.deliveryType == au.com.parcelpoint.domain.shipment.DeliveryType.EXPRESS
        secondFulfilment.deliveryType == au.com.parcelpoint.domain.shipment.DeliveryType.STANDARD
        firstFulfilment.status == au.com.parcelpoint.domain.shipment.FulfilmentStatus.COMPLETE
        secondFulfilment.status == au.com.parcelpoint.domain.shipment.FulfilmentStatus.CREATED
        firstFulfilment.shipmentType == au.com.parcelpoint.domain.shipment.FulfilmentType.HD_PFDC
        secondFulfilment.shipmentType == au.com.parcelpoint.domain.shipment.FulfilmentType.HD_PFS
        firstFulfilment.getArticles().size() == 1
        secondFulfilment.getArticles() == null || secondFulfilment.getArticles().size() == 0
        firstFulfilment.getArticles().iterator().next().status == ArticleEntity.State.COLLECTED
        order.status == OrderStatus.BOOKED
    }

    def "When order has more than one fulfilment and one of them is HD_PFDC, order should not be complete even if HD_PFDC fulfilment is confirmed and other fulfilment is partially fulfilled"(){

        init()
        ObjectMapper mapper = new ObjectMapper()
        URL resource = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/orderV4API_HD_Sku1.json")
        URL updateFulfilmentPartiallyFulfilled = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/HDFCsuccessfulUpdate.json")

        loggedInUser.setRetailerId(retailer.retailerId)

        given:
        OrderRequest orderDTO = mapper.readValue(resource, OrderRequest.class)
        orderDTO.retailerId = loggedInUser.determineRetailerIdForAuthenticatedUser();
        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        FulfilmentRequest fulfilmentRequestDTO = mapper.readValue(updateFulfilmentPartiallyFulfilled, FulfilmentRequest.class)
        FulfilmentRequest newFulfilmentRequestDTO = mapper.readValue(updateFulfilmentPartiallyFulfilled, FulfilmentRequest.class)
        BindingResult fulfilmentBindingResult = new BeanPropertyBindingResult(fulfilmentRequestDTO, "fulfilment")
        BindingResult newFulfilmentBindingResult = new BeanPropertyBindingResult(newFulfilmentRequestDTO, "fulfilment")

        when:
        SuccessResponse response = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        def order = orderApiV4_1.retrieveOrder(response.getId())

        newFulfilmentRequestDTO.setFulfilmentType(FulfilmentType.HD_PFS)
        newFulfilmentRequestDTO.setFulfilmentRef("EWN_FULFILMENT_REF")
        newFulfilmentRequestDTO.setDeliveryType(DeliveryType.STANDARD)
        newFulfilmentRequestDTO.items.iterator().next().availableQty = 0

        order = orderApiV4_1.retrieveOrder(response.getId())
        FulfilmentEntity firstFulfilment = fulfilmentRepository.findOne(Long.valueOf(order.fulfilments.get(0).fulfilmentId))

        SuccessResponse<Long> responseOfSecondFulfilmentCreation = fulfilmentAPI_V4_1.createOrderFulfilment(Long.valueOf(order.orderId),
                newFulfilmentRequestDTO, newFulfilmentBindingResult)

        order = orderApiV4_1.retrieveOrder(response.getId())

        SuccessResponse fulfilmentSuccessfulUpdate = fulfilmentAPI_V4_1.updateOrderFulfilment(
                Long.valueOf(order.orderId), Long.valueOf(firstFulfilment.id), fulfilmentRequestDTO, true, fulfilmentBindingResult)

        SuccessResponse secondFulfilmentSuccessfulUpdate = fulfilmentAPI_V4_1.updateOrderFulfilment(
                Long.valueOf(order.orderId), responseOfSecondFulfilmentCreation.id, newFulfilmentRequestDTO, true, newFulfilmentBindingResult)

        FulfilmentResponse responseOfFirstFulfilment = fulfilmentAPI_V4_1.retrieveOrderFulfilment(response.id, fulfilmentSuccessfulUpdate.id);
        FulfilmentResponse responseOfSecondFulfilment = fulfilmentAPI_V4_1.retrieveOrderFulfilment(response.id, secondFulfilmentSuccessfulUpdate.id);

        firstFulfilment = fulfilmentRepository.findOne(responseOfFirstFulfilment.getFulfilmentId())
        FulfilmentEntity secondFulfilment = fulfilmentRepository.findOne(responseOfSecondFulfilment.fulfilmentId)

        then:
        response.id  != null
        order.fulfilments.size() == 2
        firstFulfilment.deliveryType == au.com.parcelpoint.domain.shipment.DeliveryType.EXPRESS
        secondFulfilment.deliveryType == au.com.parcelpoint.domain.shipment.DeliveryType.STANDARD
        firstFulfilment.status == au.com.parcelpoint.domain.shipment.FulfilmentStatus.COMPLETE
        secondFulfilment.status == au.com.parcelpoint.domain.shipment.FulfilmentStatus.REJECTED
        firstFulfilment.shipmentType == au.com.parcelpoint.domain.shipment.FulfilmentType.HD_PFDC
        secondFulfilment.shipmentType == au.com.parcelpoint.domain.shipment.FulfilmentType.HD_PFS
        firstFulfilment.getArticles().size() == 1
        secondFulfilment.getArticles() == null || secondFulfilment.getArticles().size() == 0
        firstFulfilment.getArticles().iterator().next().status == ArticleEntity.State.COLLECTED
        order.status == OrderStatus.BOOKED
    }

    def "When order has more than one fulfilment, one is HD_PFDC and some other fulfilment, order should be booked when both are fulfilled until non HD_PFDC article is collected"(){

        init()
        ObjectMapper mapper = new ObjectMapper()
        URL resource = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/orderV4API_HD_Sku1.json")
        URL updateFulfilmentPartiallyFulfilled = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/HDFCsuccessfulUpdate.json")

        loggedInUser.setRetailerId(retailer.retailerId)

        given:
        OrderRequest orderDTO = mapper.readValue(resource, OrderRequest.class)
        orderDTO.retailerId = loggedInUser.determineRetailerIdForAuthenticatedUser();
        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        FulfilmentRequest fulfilmentRequestDTO = mapper.readValue(updateFulfilmentPartiallyFulfilled, FulfilmentRequest.class)
        FulfilmentRequest newfulfilmentRequestDTO = mapper.readValue(updateFulfilmentPartiallyFulfilled, FulfilmentRequest.class)
        BindingResult fulfilmentBindingResult = new BeanPropertyBindingResult(fulfilmentRequestDTO, "fulfilment")
        BindingResult newfulfilmentBindingResult = new BeanPropertyBindingResult(newfulfilmentRequestDTO, "fulfilment")


        when:
        SuccessResponse response = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        def order = orderApiV4_1.retrieveOrder(response.getId())

        newfulfilmentRequestDTO.setFulfilmentType(FulfilmentType.HD_PFS)
        newfulfilmentRequestDTO.setFulfilmentRef("EWN_FULFILMENT_REF")
        newfulfilmentRequestDTO.setDeliveryType(DeliveryType.STANDARD)

        order = orderApiV4_1.retrieveOrder(response.getId())
        FulfilmentEntity firstFulfilment = fulfilmentRepository.findOne(Long.valueOf(order.fulfilments.get(0).fulfilmentId))

        SuccessResponse<Long> responseOfSecondFulfilment = fulfilmentAPI_V4_1.createOrderFulfilment(Long.valueOf(order.orderId),
                newfulfilmentRequestDTO, newfulfilmentBindingResult)

        order = orderApiV4_1.retrieveOrder(response.getId())

        SuccessResponse fulfilmentSuccessfulUpdate = fulfilmentAPI_V4_1.updateOrderFulfilment(
                Long.valueOf(response.id), Long.valueOf(firstFulfilment.id), fulfilmentRequestDTO, true, fulfilmentBindingResult)

        fulfilmentAPI_V4_1.confirmFulfilment(responseOfSecondFulfilment.id)
        FulfilmentResponse responseOfFirstFulfilment = fulfilmentAPI_V4_1.retrieveOrderFulfilment(response.id, fulfilmentSuccessfulUpdate.id);

        firstFulfilment = fulfilmentRepository.findOne(responseOfFirstFulfilment.getFulfilmentId())
        FulfilmentEntity secondFulfilment = fulfilmentRepository.findOne(responseOfSecondFulfilment.id)

        then:
        response.id  != null
        order.fulfilments.size() == 2
        firstFulfilment.deliveryType == au.com.parcelpoint.domain.shipment.DeliveryType.EXPRESS
        secondFulfilment.deliveryType == au.com.parcelpoint.domain.shipment.DeliveryType.STANDARD
        firstFulfilment.status == au.com.parcelpoint.domain.shipment.FulfilmentStatus.COMPLETE
        secondFulfilment.status == au.com.parcelpoint.domain.shipment.FulfilmentStatus.FULFILLED
        firstFulfilment.shipmentType == au.com.parcelpoint.domain.shipment.FulfilmentType.HD_PFDC
        secondFulfilment.shipmentType == au.com.parcelpoint.domain.shipment.FulfilmentType.HD_PFS
        firstFulfilment.getArticles().size() == 1
        secondFulfilment.getArticles().size() == 1
        firstFulfilment.getArticles().iterator().next().status == ArticleEntity.State.COLLECTED
        secondFulfilment.getArticles().iterator().next().status == ArticleEntity.State.AWAITING_COLLECTION
        order.status == OrderStatus.BOOKED
    }

    def "Order should be marked as COMPLETE if one & only HD_PFDC fulfilment is partially fulfilled"(){

        init()
        ObjectMapper mapper = new ObjectMapper()
        URL resource = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/orderV4API_HD_Sku1.json")
        URL updateFulfilmentPartiallyFulfilled = getClass().getClassLoader().getResource("${RESOURCES_FOLDER}/HDFCsuccessfulUpdate.json")

        loggedInUser.setRetailerId(retailer.retailerId)

        given:
        OrderRequest orderDTO = mapper.readValue(resource, OrderRequest.class)
        orderDTO.retailerId = loggedInUser.determineRetailerIdForAuthenticatedUser();
        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        FulfilmentRequest fulfilmentRequestDTO = mapper.readValue(updateFulfilmentPartiallyFulfilled, FulfilmentRequest.class)
        fulfilmentRequestDTO.items.iterator().next().availableQty = 1
        fulfilmentRequestDTO.items.iterator().next().requestedQty = 2
        BindingResult fulfilmentBindingResult = new BeanPropertyBindingResult(fulfilmentRequestDTO, "fulfilment")

        when:
        SuccessResponse response = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        def order = orderApiV4_1.retrieveOrder(response.getId())
        SuccessResponse fulfilmentSuccessfulUpdate = fulfilmentAPI_V4_1.updateOrderFulfilment(
                Long.valueOf(response.id), Long.valueOf(order.fulfilments[0].fulfilmentId), fulfilmentRequestDTO, true, fulfilmentBindingResult)

        FulfilmentResponse fulfilmentResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilment(response.id, fulfilmentSuccessfulUpdate.id);
        FulfilmentEntity partiallyFulfilledFulfilment = fulfilmentRepository.findOne(fulfilmentResponse.getFulfilmentId())
        order = orderApiV4_1.retrieveOrder(response.getId())
        then:
        response.id > 0
        order.fulfilments.size() == 2
        fulfilmentResponse.deliveryType == DeliveryType.EXPRESS
        partiallyFulfilledFulfilment.order.status == au.com.parcelpoint.domain.order.OrderStatus.COMPLETE
        partiallyFulfilledFulfilment.status == au.com.parcelpoint.domain.shipment.FulfilmentStatus.COMPLETE
        fulfilmentResponse.fulfilmentType == FulfilmentType.HD_PFDC
        partiallyFulfilledFulfilment.getArticles().size() == 1
        partiallyFulfilledFulfilment.getArticles().iterator().next().status == ArticleEntity.State.COLLECTED

        where:
        fileName                                      | value   | rejectFulfilmentFileName
        "${RESOURCES_FOLDER}/orderV4API_HD_Sku1.json" | "value" | "${RESOURCES_FOLDER}/rejectFulfilmentSku1.json"
    }

    def "Retrieve fulfilment article"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilment1FileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse orderFulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        SuccessResponse confirmFulfilmentResponse = fulfilmentAPI_V4_1.confirmFulfilment(orderFulfilmentResponse.id)
        SuccessResponse bookCourierFulfilmentResponse = fulfilmentAPIV4.bookCourier(confirmFulfilmentResponse.id)
        FulfilmentArticleResponse fulfilmentArticleResponse = fulfilmentAPI_V4_1.retrieveOrderFulfilmentArticle(orderResponse.id, orderFulfilmentResponse.id)

        then:
        fulfilmentArticleResponse != null
        fulfilmentArticleResponse.getArticleConsignments() != null
        fulfilmentArticleResponse.getArticleConsignments().size() > 0
        fulfilmentArticleResponse.getArticleConsignments().toList().get(0).article != null
        fulfilmentArticleResponse.getArticleConsignments().toList().get(0).consignment != null

        where:
        fileName                          | value   | fulfilment1FileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json"
    }

    def "Create article, article items and consignment for article POST API"() {
        loggedInUser.setRetailerId(1)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilmentFileName)
        URL articleRequestResource = getClass().getClassLoader().getResource(articleFileName)

        given:
        Order orderDTO = mapper.readValue(orderResource, Order.class)
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        FulfilmentArticleRequest articleRequest = mapper.readValue(articleRequestResource, FulfilmentArticleRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")
        orderDTO.retailer.retailerId = retailer.retailerId

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApi.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse fulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        SuccessResponse articleResponse = fulfilmentAPI_V4_1.createArticleConsignment(orderResponse.id, fulfilmentResponse.id, articleRequest)
        FulfilmentEntity fulfilment = fulfilmentService.retrieveById(fulfilmentResponse.id)
        ArticleEntity article = articleService.getArticleEntityById(articleResponse.id)

        then:
        articleResponse != null
        article.getExternalReference() == articleRequest.getArticleRef()
        !CollectionUtils.isEmpty(fulfilment.getArticles())
        !CollectionUtils.isEmpty(article.getArticleItems())
        article.getArticleItems().size() == articleRequest.getItems().size()
        article.getArticleItems().get(0).getQuantity().toString() == articleRequest.getItems().getAt(0).getQuantity()
        article.getArticleItems().get(0).getSkuRef() == articleRequest.getItems().getAt(0).getSkuRef()

        where:
        fileName                          | value   | fulfilmentFileName                     | articleFileName
        "${RESOURCES_FOLDER}/order1.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/article1.json"
    }

    def "Mark article as awaiting arrival successfully"() {
        createRetailer452()
        loggedInUser.setRetailerId(452L)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilmentFileName)
        URL articleRequestResource = getClass().getClassLoader().getResource(articleFileName)

        given:
        OrderRequest orderDTO = mapper.readValue(orderResource, OrderRequest.class)
        orderDTO.retailerId = retailer.retailerId
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        fulfilmentRequest.consignment.retailerId = retailer.retailerId
        FulfilmentArticleRequest articleRequest = mapper.readValue(articleRequestResource, FulfilmentArticleRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse fulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        SuccessResponse articleResponse = fulfilmentAPI_V4_1.createArticleConsignment(orderResponse.id, fulfilmentResponse.id, articleRequest)
        fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "awaiting_arrival", new BeanPropertyBindingResult("awaiting_arrival", "status"))
        ArticleEntity article = articleService.getArticleEntityById(articleResponse.id)

        then:
        article != null
        article.getStatus() == ArticleEntity.State.AWAITING_ARRIVAL

        where:
        fileName                              | value   | fulfilmentFileName                     | articleFileName
        "${RESOURCES_FOLDER}/orderV4API.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/article1.json"
    }

    def "Mark article as awaiting arrival from illegal status"() {
        createRetailer452()
        loggedInUser.setRetailerId(452L)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilmentFileName)
        URL articleRequestResource = getClass().getClassLoader().getResource(articleFileName)

        given:
        OrderRequest orderDTO = mapper.readValue(orderResource, OrderRequest.class)
        orderDTO.retailerId = retailer.retailerId
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        fulfilmentRequest.consignment.retailerId = retailer.retailerId
        FulfilmentArticleRequest articleRequest = mapper.readValue(articleRequestResource, FulfilmentArticleRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse fulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        SuccessResponse articleResponse = fulfilmentAPI_V4_1.createArticleConsignment(orderResponse.id, fulfilmentResponse.id, articleRequest)
        ArticleEntity articleEntity = articleService.getArticleEntityById(articleResponse.id)
        articleEntity.status = ArticleEntity.State.CANCELLED
        articleService.update(articleEntity)

        Boolean exceptionCaught = false
        try {
            fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "awaiting_arrival", new BeanPropertyBindingResult("awaiting_arrival", "status"))
        } catch (ClientException e) {
            exceptionCaught = true
        }

        then:
        exceptionCaught

        where:
        fileName                              | value   | fulfilmentFileName                     | articleFileName
        "${RESOURCES_FOLDER}/orderV4API.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/article1.json"
    }

    def "Mark article as awaiting collection from created successfully"() {
        createRetailer452()
        loggedInUser.setRetailerId(452L)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilmentFileName)
        URL articleRequestResource = getClass().getClassLoader().getResource(articleFileName)

        given:
        OrderRequest orderDTO = mapper.readValue(orderResource, OrderRequest.class)
        orderDTO.retailerId = retailer.retailerId
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        fulfilmentRequest.consignment.retailerId = retailer.retailerId
        FulfilmentArticleRequest articleRequest = mapper.readValue(articleRequestResource, FulfilmentArticleRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse fulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        SuccessResponse articleResponse = fulfilmentAPI_V4_1.createArticleConsignment(orderResponse.id, fulfilmentResponse.id, articleRequest)
        fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "awaiting_collection", new BeanPropertyBindingResult("awaiting_collection", "status"))
        ArticleEntity article = articleService.getArticleEntityById(articleResponse.id)

        then:
        article != null
        article.getStatus() == ArticleEntity.State.AWAITING_COLLECTION

        where:
        fileName                              | value   | fulfilmentFileName                     | articleFileName
        "${RESOURCES_FOLDER}/orderV4API.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/article1.json"
    }

    def "Mark article as awaiting collection from awaiting arrival successfully"() {
        createRetailer452()
        loggedInUser.setRetailerId(452L)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilmentFileName)
        URL articleRequestResource = getClass().getClassLoader().getResource(articleFileName)

        given:
        OrderRequest orderDTO = mapper.readValue(orderResource, OrderRequest.class)
        orderDTO.retailerId = retailer.retailerId
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        fulfilmentRequest.consignment.retailerId = retailer.retailerId
        FulfilmentArticleRequest articleRequest = mapper.readValue(articleRequestResource, FulfilmentArticleRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse fulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        SuccessResponse articleResponse = fulfilmentAPI_V4_1.createArticleConsignment(orderResponse.id, fulfilmentResponse.id, articleRequest)
        fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "awaiting_arrival", new BeanPropertyBindingResult("awaiting_arrival", "status"))
        fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "awaiting_collection", new BeanPropertyBindingResult("awaiting_collection", "status"))
        ArticleEntity article = articleService.getArticleEntityById(articleResponse.id)

        then:
        article != null
        article.getStatus() == ArticleEntity.State.AWAITING_COLLECTION

        where:
        fileName                              | value   | fulfilmentFileName                     | articleFileName
        "${RESOURCES_FOLDER}/orderV4API.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/article1.json"
    }

    def "Mark article as awaiting collection from illegal status"() {
        createRetailer452()
        loggedInUser.setRetailerId(452L)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilmentFileName)
        URL articleRequestResource = getClass().getClassLoader().getResource(articleFileName)

        given:
        OrderRequest orderDTO = mapper.readValue(orderResource, OrderRequest.class)
        orderDTO.retailerId = retailer.retailerId
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        fulfilmentRequest.consignment.retailerId = retailer.retailerId
        FulfilmentArticleRequest articleRequest = mapper.readValue(articleRequestResource, FulfilmentArticleRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse fulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        SuccessResponse articleResponse = fulfilmentAPI_V4_1.createArticleConsignment(orderResponse.id, fulfilmentResponse.id, articleRequest)
        ArticleEntity articleEntity = articleService.getArticleEntityById(articleResponse.id)
        articleEntity.status = ArticleEntity.State.CANCELLED
        articleService.update(articleEntity)

        Boolean exceptionCaught = false
        try {
            fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "awaiting_collection", new BeanPropertyBindingResult("awaiting_collection", "status"))
        } catch (ClientException e) {
            exceptionCaught = true
        }

        then:
        exceptionCaught

        where:
        fileName                              | value   | fulfilmentFileName                     | articleFileName
        "${RESOURCES_FOLDER}/orderV4API.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/article1.json"
    }

    def "Mark article as collected successfully"() {
        createRetailer452()
        loggedInUser.setRetailerId(452L)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilmentFileName)
        URL articleRequestResource = getClass().getClassLoader().getResource(articleFileName)

        given:
        OrderRequest orderDTO = mapper.readValue(orderResource, OrderRequest.class)
        orderDTO.retailerId = retailer.retailerId
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        fulfilmentRequest.consignment.retailerId = retailer.retailerId
        FulfilmentArticleRequest articleRequest = mapper.readValue(articleRequestResource, FulfilmentArticleRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse fulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        SuccessResponse articleResponse = fulfilmentAPI_V4_1.createArticleConsignment(orderResponse.id, fulfilmentResponse.id, articleRequest)
        fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "awaiting_collection", new BeanPropertyBindingResult("awaiting_collection",
                "status"))
        fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "collected", new BeanPropertyBindingResult("collected", "status"))
        ArticleEntity article = articleService.getArticleEntityById(articleResponse.id)

        then:
        article != null
        article.getStatus() == ArticleEntity.State.COLLECTED

        where:
        fileName                              | value   | fulfilmentFileName                     | articleFileName
        "${RESOURCES_FOLDER}/orderV4API.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/article1.json"
    }

    def "Mark article as collected from illegal status"() {
        createRetailer452()
        loggedInUser.setRetailerId(452L)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilmentFileName)
        URL articleRequestResource = getClass().getClassLoader().getResource(articleFileName)

        given:
        OrderRequest orderDTO = mapper.readValue(orderResource, OrderRequest.class)
        orderDTO.retailerId = retailer.retailerId
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        fulfilmentRequest.consignment.retailerId = retailer.retailerId
        FulfilmentArticleRequest articleRequest = mapper.readValue(articleRequestResource, FulfilmentArticleRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse fulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        SuccessResponse articleResponse = fulfilmentAPI_V4_1.createArticleConsignment(orderResponse.id, fulfilmentResponse.id, articleRequest)

        Boolean exceptionCaught = false
        try {
            fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "collected", new BeanPropertyBindingResult("collected", "status"))
        } catch (ClientException e) {
            exceptionCaught = true
        }

        then:
        exceptionCaught

        where:
        fileName                              | value   | fulfilmentFileName                     | articleFileName
        "${RESOURCES_FOLDER}/orderV4API.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/article1.json"
    }


    def "Mark article as cancelled successfully"() {
        createRetailer452()
        loggedInUser.setRetailerId(452L)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilmentFileName)
        URL articleRequestResource = getClass().getClassLoader().getResource(articleFileName)

        given:
        OrderRequest orderDTO = mapper.readValue(orderResource, OrderRequest.class)
        orderDTO.retailerId = retailer.retailerId
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        fulfilmentRequest.consignment.retailerId = retailer.retailerId
        FulfilmentArticleRequest articleRequest = mapper.readValue(articleRequestResource, FulfilmentArticleRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse fulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        SuccessResponse articleResponse = fulfilmentAPI_V4_1.createArticleConsignment(orderResponse.id, fulfilmentResponse.id, articleRequest)
        fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "awaiting_collection", new BeanPropertyBindingResult("awaiting_collection", "status"))
        fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "cancelled", new BeanPropertyBindingResult("cancelled", "status"))
        ArticleEntity article = articleService.getArticleEntityById(articleResponse.id)
        FulfilmentEntity fulfilment = fulfilmentService.retrieveById(fulfilmentResponse.id)

        then:
        article != null
        fulfilment != null
        article.getStatus() == ArticleEntity.State.CANCELLED
        fulfilment.getStatus() == au.com.parcelpoint.domain.shipment.FulfilmentStatus.COMPLETE

        where:
        fileName                              | value   | fulfilmentFileName                     | articleFileName
        "${RESOURCES_FOLDER}/orderV4API.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/article1.json"
    }

    def "Mark article as cancelled from illegal status"() {
        createRetailer452()
        loggedInUser.setRetailerId(452L)
        init()
        ObjectMapper mapper = new ObjectMapper()
        URL orderResource = getClass().getClassLoader().getResource(fileName)
        URL fulfilmentRequestResource = getClass().getClassLoader().getResource(fulfilmentFileName)
        URL articleRequestResource = getClass().getClassLoader().getResource(articleFileName)

        given:
        OrderRequest orderDTO = mapper.readValue(orderResource, OrderRequest.class)
        orderDTO.retailerId = retailer.retailerId
        FulfilmentRequest fulfilmentRequest = mapper.readValue(fulfilmentRequestResource, FulfilmentRequest.class)
        fulfilmentRequest.consignment.retailerId = retailer.retailerId
        FulfilmentArticleRequest articleRequest = mapper.readValue(articleRequestResource, FulfilmentArticleRequest.class)

        BindingResult orderBindingResult = new BeanPropertyBindingResult(orderDTO, "order")
        BindingResult fulfilmentRequestBindingResult = new BeanPropertyBindingResult(fulfilmentRequest, "fulfilmentRequest")

        when:
        loggedInUser.setRetailerId(retailer.retailerId)
        SuccessResponse orderResponse = orderApiV4_1.bookNewOrder(orderDTO, orderBindingResult)
        SuccessResponse fulfilmentResponse = fulfilmentAPI_V4_1.createOrderFulfilment(orderResponse.id, fulfilmentRequest, fulfilmentRequestBindingResult)
        SuccessResponse articleResponse = fulfilmentAPI_V4_1.createArticleConsignment(orderResponse.id, fulfilmentResponse.id, articleRequest)
        fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "awaiting_collection", new BeanPropertyBindingResult("awaiting_collection", "status"))
        fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "collected", new BeanPropertyBindingResult("collected", "status"))

        Boolean exceptionCaught = false
        try {
            fulfilmentAPI_V4_1.markArticleAs(orderResponse.id, fulfilmentResponse.id, articleResponse.id, "cancelled", new BeanPropertyBindingResult("cancelled", "status"))
        } catch (ClientException e) {
            exceptionCaught = true
        }

        ArticleEntity article = articleService.getArticleEntityById(articleResponse.id)

        then:
        exceptionCaught
        article != null
        article.getStatus() == ArticleEntity.State.COLLECTED

        where:
        fileName                              | value   | fulfilmentFileName                     | articleFileName
        "${RESOURCES_FOLDER}/orderV4API.json" | "value" | "${RESOURCES_FOLDER}/fulfilment1.json" | "${RESOURCES_FOLDER}/article1.json"
    }

    def createRetailer452() {
        Sql sql = Sql.newInstance(env.getProperty("db.url") + ';AUTO_SERVER=TRUE', env.getProperty("db.username"), env.getProperty("db.password"), env.getProperty("db.driver"))
        sql.execute("INSERT into retailer(id, name) values(452L, 'Farmers')")
    }
}