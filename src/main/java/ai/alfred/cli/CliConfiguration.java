package ai.alfred.cli;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CliProperties.class)
class CliConfiguration {

	@Bean
	FilterRegistrationBean<ChannelSecurityFilter> channelSecurityFilter(DaemonToken token) {
		FilterRegistrationBean<ChannelSecurityFilter> registration = new FilterRegistrationBean<>(
				new ChannelSecurityFilter(token));
		registration.addUrlPatterns("/*");
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

}
