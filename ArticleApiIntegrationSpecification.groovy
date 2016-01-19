package au.com.parcelpoint.api.integration.v4_1import au.com.parcelpoint.api.helper.ObjectBuilderimport au.com.parcelpoint.api.helper.TestIsolatorimport au.com.parcelpoint.api.web.config.AppProfileimport au.com.parcelpoint.api.web.config.AppTestConfigimport au.com.parcelpoint.api.web.config.security.DevAPIClientimport au.com.parcelpoint.api.web.controllers.v4.*import au.com.parcelpoint.api.web.controllers.v4_1.ArticleApiimport au.com.parcelpoint.domain.legacy.agent.*import au.com.parcelpoint.domain.legacy.carrier.CarrierRepositoryimport au.com.parcelpoint.domain.legacy.location.AgentLocationEntityimport au.com.parcelpoint.domain.legacy.location.LocationRepositoryimport au.com.parcelpoint.domain.legacy.parcel.ArticleEntityimport au.com.parcelpoint.domain.legacy.retailer.RetailerRepositoryimport au.com.parcelpoint.domain.order.CustomerOrderEntityimport au.com.parcelpoint.domain.order.OrderRepositoryimport au.com.parcelpoint.domain.shipment.FulfilmentItemRepositoryimport au.com.parcelpoint.domain.shipment.FulfilmentRepositoryimport au.com.parcelpoint.domain.sku.SkuLocationInventoryRepositoryimport au.com.parcelpoint.domain.sku.SkuRepositoryimport au.com.parcelpoint.domain.user.AgentUserEntityimport au.com.parcelpoint.domain.user.UserRepositoryimport au.com.parcelpoint.pojo.*import au.com.parcelpoint.pojo.v4_1.fulfilment.FulfilmentArticleRequestimport au.com.parcelpoint.services.article.ArticleServiceimport au.com.parcelpoint.services.fulfilment.FulfilmentServiceimport au.com.parcelpoint.services.retailer.RetailerServiceimport au.com.parcelpoint.test.ObjectMotherUtilimport com.fasterxml.jackson.databind.ObjectMapperimport com.google.common.collect.Listsimport org.springframework.beans.factory.annotation.Autowiredimport org.springframework.test.context.ActiveProfilesimport org.springframework.test.context.ContextConfigurationimport org.springframework.test.context.transaction.AfterTransactionimport org.springframework.test.context.web.WebAppConfigurationimport org.springframework.validation.BeanPropertyBindingResultimport org.springframework.validation.BindingResultimport spock.lang.Sharedimport spock.lang.Specificationimport javax.transaction.Transactional