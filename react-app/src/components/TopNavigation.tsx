import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import LogoutButton from './LogoutButton';

export default function TopNavigation() {
  return (
    <Navbar variant="light" expand="lg">
      <Container>
        <Navbar.Brand href="/">Home</Navbar.Brand>
        <Navbar.Toggle aria-controls="basic-navbar-nav" />
        <Navbar.Collapse id="basic-navbar-nav">
          <Nav className="me-auto">
            <Nav.Link href="portfolio">Portfolio</Nav.Link>
            <Nav.Link href="watchlist">Watch List</Nav.Link>
            <Nav.Link href="snp500prices">S&P500 Prices</Nav.Link>
            <Nav.Link href="education">Suggestions</Nav.Link>
            <Nav.Link href="register">Register</Nav.Link>
            {/*<Nav.Link href="outliers">Outliers</Nav.Link>*/}
            <Nav.Link href="restaurants">Restaurants</Nav.Link>
            <Nav.Link href="restaurant-recommend">Restaurant Recommend</Nav.Link>
            <Nav.Link href="entertainment">Entertainment</Nav.Link>
            <LogoutButton/>
          </Nav>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}