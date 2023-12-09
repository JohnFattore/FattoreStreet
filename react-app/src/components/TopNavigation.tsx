import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';

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
            <Nav.Link href="transaction">Transaction</Nav.Link>
            <Nav.Link href="watchlist">Watch List</Nav.Link>
            <Nav.Link href="login">Login</Nav.Link>
            <Nav.Link href="register">Register</Nav.Link>
            <Nav.Link href="philosophy">Philosophy</Nav.Link>
            <Nav.Link href="entertainment">Entertainment</Nav.Link>
          </Nav>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}

export default TopNavigation;