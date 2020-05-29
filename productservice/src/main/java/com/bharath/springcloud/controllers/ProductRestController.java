package com.bharath.springcloud.controllers;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.bharath.springcloud.model.Coupon;
import com.bharath.springcloud.model.Product;
import com.bharath.springcloud.repos.ProductRepo;
import com.bharath.springcloud.restclients.CouponClient;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

@RestController
@RequestMapping("/productapi")
public class ProductRestController {

	@Autowired
	CouponClient couponClient;

	@Autowired
	private ProductRepo repo;
	
	

	@HystrixCommand(fallbackMethod = "sendErrorResponse")
	@RequestMapping(value = "/products", method = RequestMethod.POST)
	public Product create(@RequestBody Product product) {
		Coupon coupon = couponClient.getCoupon(product.getCouponCode());
		product.setPrice(product.getPrice().subtract(coupon.getDiscount()));
		return repo.save(product);

	}

	public Product sendErrorResponse(Product product) {
		return product;

	}
	
	
	@Autowired
	RestTemplate restTemplate;
	
	@Autowired
	DiscoveryClient discoveryClient;
	
	@Value("#{null)")
	private Random random;
	
	@PostMapping(value="/private/v1/eod/details/retrieve", produces= {"application/json"}, consumes= {"application/json"})
	public ResponseEntity<?> generateEODReport(@RequestHeader(value="uuid",required=true) String uuid,@RequestHeader(
			value="channelId", defaultValue="RTLAO", required=false) String channelId, @RequestBody Product product)
	{
		
		
		HttpHeaders headers = new HttpHeaders();
		headers.add("uuid", uuid);
		headers.add("channelId", channelId);
		headers.add("Accept", "application/json");
		headers.add("Content-Type", "application/json");
		
		HttpEntity<Product> requestEntity = new HttpEntity<>(product,headers);
		
		URI serviceUri= getURI("COUPON-SERVICE", "/private/v1/eod/details/retrieve");
		
		if(null != serviceUri)
		{
			ResponseEntity<String> result = restTemplate.exchange(serviceUri.toString(), HttpMethod.POST, requestEntity, String.class);
			result.getBody();
		}
		
		return new ResponseEntity<>(HttpStatus.OK);
		
	}

	private URI getURI(String serviceName, String endPoint) {
		String serviceUrl = getServiceUrl(serviceName);
		
		UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(serviceUrl+ endPoint);
		URI uri = builder.build().encode().toUri();
		
		return uri;
	}
	
	
	private URI getPramsURI(String serviceName, String endPoint,Map<String,String> paramMap) {
		String serviceUrl = getServiceUrl(serviceName);
		
		UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(serviceUrl+ endPoint);
		URI uri = builder.buildAndExpand(paramMap).encode().toUri();
		
		return uri;
	}
	

	private String getServiceUrl(String serviceName) {
		String serviceUrl = "";
		if("activeProfile".contains("LOCAL"))
		{
			if(serviceName.equals("COUPON-SERVICE"))
			{
				serviceUrl = "http://localhost:8080";
			}
		}
		else
		{
			List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
			if(null != instances)
			{
				ServiceInstance instance = instances.get(random.nextInt(instances.size()));
				if(null != instance)
					return instance.getUri().toString();
			}
			
		}
		return null;
	}

}
