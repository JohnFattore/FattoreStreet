import Container from "react-bootstrap/Container";
import Nav from "react-bootstrap/Nav";
import Navbar from "react-bootstrap/Navbar";
import LogoutButton from "./LogoutButton";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { Link, NavLink, useLocation } from "react-router-dom";

// Pages grouped under the Portfolio tab. Restaurants and Entertainment are
// deliberately left out of the nav; their routes still resolve directly.
const portfolioLinks = [
  { to: "/portfolio", label: "Portfolio" },
  { to: "/visualizer", label: "Allocation" },
  { to: "/watchlist", label: "Watchlist" },
];

// The group's own pages plus account detail, which is reached from Portfolio.
const portfolioPaths = [...portfolioLinks.map(({ to }) => to), "/account"];

// Match on a path boundary so "/portfolios" doesn't count as "/portfolio".
function isPortfolioPath(pathname: string) {
  return portfolioPaths.some(
    (path) => pathname === path || pathname.startsWith(`${path}/`),
  );
}

export default function TopNavigation() {
  const { access } = useSelector((state: RootState) => state.user);
  const { pathname } = useLocation();
  const inPortfolio = isPortfolioPath(pathname);

  return (
    <>
      <Navbar expand="lg">
        <Container>
          <Navbar.Brand as={Link} to="/">
            Fattore Street
          </Navbar.Brand>
          <Navbar.Toggle aria-controls="basic-navbar-nav" />
          <Navbar.Collapse id="basic-navbar-nav">
            <Nav>
              <Nav.Link as={NavLink} to="/chatbot">
                Chatbot
              </Nav.Link>
              <Nav.Link as={NavLink} to="/indexes">
                Indexes
              </Nav.Link>
              <Nav.Link as={NavLink} to="/economic-indicators">
                Economic Indicators
              </Nav.Link>
              <Nav.Link as={NavLink} to="/compare">
                Compare
              </Nav.Link>
              {/* Link, not NavLink: NavLink would only light up on /portfolio,
                  but this tab should stay active across the whole group. */}
              <Nav.Link as={Link} to="/portfolio" active={inPortfolio}>
                Portfolio
              </Nav.Link>
              <Nav.Link as={NavLink} to="/blog">
                Blog
              </Nav.Link>
              <Nav.Link as={NavLink} to="/user">
                Profile
              </Nav.Link>
              {access && <LogoutButton />}
            </Nav>
          </Navbar.Collapse>
        </Container>
      </Navbar>
      {inPortfolio && (
        <Container>
          <Nav as="nav" variant="underline" aria-label="Portfolio">
            {portfolioLinks.map(({ to, label }) => (
              <Nav.Link key={to} as={NavLink} to={to}>
                {label}
              </Nav.Link>
            ))}
          </Nav>
        </Container>
      )}
    </>
  );
}
