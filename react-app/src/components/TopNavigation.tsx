import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import { Button } from 'react-bootstrap';

function TopNavigation() {
  return (
    <Navbar variant="light" expand="lg">
      <Container>
        <Navbar.Brand href="/">Home</Navbar.Brand>
        <Navbar.Toggle aria-controls="basic-navbar-nav" />
        <Navbar.Collapse id="basic-navbar-nav">
          <Nav className="me-auto">
            <Nav.Link href="portfolio">Portfolio</Nav.Link>
            <Nav.Link href="allocation">Allocation</Nav.Link>
            <Nav.Link href="watchlist">Watch List</Nav.Link>
            <Nav.Link href="restaurants">Restaurants</Nav.Link>
            <Nav.Link href="education">Suggestions</Nav.Link>
            <Nav.Link href="register">Register</Nav.Link>
            <Nav.Link href="outliers">Outliers</Nav.Link>
            <Nav.Link href="entertainment">Entertainment</Nav.Link>
            <Button
              onClick={() => {
                sessionStorage.removeItem("token");
                sessionStorage.removeItem("refresh");
                alert("You are logged out");
              }}>Logout</Button>
          </Nav>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}

export default TopNavigation;