package fixture.violation;

import org.springframework.modulith.Modulithic;

/**
 * Root of a deliberately broken two-module layout: {@code beta} reaches into {@code alpha.internal}. Kept outside
 * {@code ai.alfred} so it never becomes part of alfred's own module model.
 */
@Modulithic
public class FixtureApplication {
}
