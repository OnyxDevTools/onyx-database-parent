package com.onyxdevtools.example.querying;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;

import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;

import com.onyxdevtools.example.querying.entities.Conference;
import com.onyxdevtools.example.querying.entities.Division;
import com.onyxdevtools.example.querying.entities.League;
import com.onyxdevtools.example.querying.entities.Player;
import com.onyxdevtools.example.querying.entities.Season;
import com.onyxdevtools.example.querying.entities.Stats;
import com.onyxdevtools.example.querying.entities.Team;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

//J-
public class Main extends AbstractDemo
{

    public static void main(final String[] args) throws IOException
    {
        final String pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox"
                + File.separatorChar + "querying-db.oxd";

        // Delete database so you have a clean slate
        deleteDatabase(pathToOnyxDB);

        final PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
        factory.setCredentials("onyx-user", "SavingDataIsFun!");
        factory.setDatabaseLocation(pathToOnyxDB);
        factory.initialize();
        final PersistenceManager manager = factory.getPersistenceManager();

        seedData(manager);

        factory.close(); //close the factory so that we can use it again

        System.out.print("\n Find Example \n");
        FindExample.demo();

        System.out.print("\nFindById Example \n");
        FindByIdExample.demo();

        System.out.print("\nList Example \n");
        ListExample.demo();

        System.out.print("\nQuery Example \n");
        QueryExample.demo();
    }

    public static void seedData(PersistenceManager manager) throws InitializationException, EntityException
    {

        //Create league
        League nfl = new League();
        nfl.setName("NFL");
        nfl.setDescription("National Football League");

        //create a season
        Season season2015 = new Season();
        season2015.setYear(2015);

        //add the season to the league
        nfl.getSeasons().add(season2015);

        //create conferences
        Conference afc = new Conference();
        afc.setName("AFC");

        Conference nfc = new Conference();
        nfc.setName("NFC");

        //create divisions
        Division afcNorth = new Division();
        afcNorth.setName("AFC NORTH");

        Division afcSouth = new Division();
        afcSouth.setName("AFC SOUTH");

        Division afcEast = new Division();
        afcEast.setName("AFC EAST");

        Division afcWest = new Division();
        afcWest.setName("AFC WEST");

        Division nfcNorth = new Division();
        nfcNorth.setName("NFC NORTH");

        Division nfcSouth = new Division();
        nfcSouth.setName("NFC SOUTH");

        Division nfcEast = new Division();
        nfcEast.setName("NFC EAST");

        Division nfcWest = new Division();
        nfcWest.setName("NFC WEST");

        //Add conferences to the season
        season2015.getConferences().add(nfc);
        season2015.getConferences().add(afc);

        //Add divisions to conferences
        nfc.getDivisions().add(nfcNorth);
        nfc.getDivisions().add(nfcSouth);
        nfc.getDivisions().add(nfcEast);
        nfc.getDivisions().add(nfcWest);

        afc.getDivisions().add(afcNorth);
        afc.getDivisions().add(afcSouth);
        afc.getDivisions().add(afcEast);
        afc.getDivisions().add(afcWest);

        //Create teams
        Team raiders = new Team();
        raiders.setTeamName("Raiders");

        Team broncos = new Team();
        broncos.setTeamName("Broncos");

        Team cheifs = new Team();
        cheifs.setTeamName("Cheifs");

        Team chargers = new Team();
        chargers.setTeamName("Chargers");

        Team patriots = new Team();
        patriots.setTeamName("Patriots");

        Team bills = new Team();
        bills.setTeamName("Bills");

        Team jets = new Team();
        jets.setTeamName("Jets");

        Team dolphins = new Team();
        dolphins.setTeamName("Dolphins");

        Team texans = new Team();
        texans.setTeamName("Texans");

        Team colts = new Team();
        colts.setTeamName("Colts");

        Team jaguars = new Team();
        jaguars.setTeamName("Jaguars");

        Team titans = new Team();
        titans.setTeamName("Titans");

        Team steelers = new Team();
        steelers.setTeamName("Steelers");

        Team bengals = new Team();
        bengals.setTeamName("Bengals");

        Team browns = new Team();
        browns.setTeamName("Browns");

        Team ravens = new Team();
        ravens.setTeamName("Ravens");

        //NFC
        Team seahawks = new Team();
        seahawks.setTeamName("Seahawks");

        Team rams = new Team();
        rams.setTeamName("Rams");

        Team niners = new Team();
        niners.setTeamName("49ers");

        Team cardinals = new Team();
        cardinals.setTeamName("Cardinals");

        Team panthers = new Team();
        panthers.setTeamName("Panthers");

        Team falcons = new Team();
        falcons.setTeamName("Falcons");

        Team buccaneers = new Team();
        buccaneers.setTeamName("Buccaneers");

        Team saints = new Team();
        saints.setTeamName("Saints");

        Team cowboys = new Team();
        cowboys.setTeamName("Cowboys");

        Team eagles = new Team();
        eagles.setTeamName("Eagles");

        Team giants = new Team();
        giants.setTeamName("Giants");

        Team redskins = new Team();
        redskins.setTeamName("Redskins");

        Team bears = new Team();
        bears.setTeamName("Bears");

        Team lions = new Team();
        lions.setTeamName("Lions");

        Team packers = new Team();
        packers.setTeamName("Packers");

        Team vikings = new Team();
        vikings.setTeamName("Vikings");

        //Add teams to divisions
        afcWest.setTeams(Arrays.asList(new Team[]
        {
            raiders, broncos, cheifs, chargers
        }));

        afcSouth.setTeams(Arrays.asList(new Team[]
        {
            colts, texans, jaguars, titans
        }));

        afcEast.setTeams(Arrays.asList(new Team[]
        {
            patriots, bills, jets, dolphins
        }));

        afcNorth.setTeams(Arrays.asList(new Team[]
        {
            steelers, bengals, browns, ravens
        }));

        nfcWest.setTeams(Arrays.asList(new Team[]
        {
            seahawks, niners, rams, cardinals
        }));

        nfcSouth.setTeams(Arrays.asList(new Team[]
        {
            panthers, falcons, saints, buccaneers
        }));

        nfcEast.setTeams(Arrays.asList(new Team[]
        {
            cowboys, eagles, giants, redskins
        }));

        nfcNorth.setTeams(Arrays.asList(new Team[]
        {
            bears, lions, vikings, packers
        }));

        //Add players to each team
        //AFC WEST
        //Raiders
        Player raidersQB = new Player();
        raidersQB.setFirstName("Derick");
        raidersQB.setLastName("Carr");
        raidersQB.setPosition("QB");

        Player raidersRB1 = new Player();
        raidersRB1.setFirstName("Latavius");
        raidersRB1.setLastName("Murray");
        raidersRB1.setPosition("RB");

        Player raidersRB2 = new Player();
        raidersRB2.setFirstName("Taiwan");
        raidersRB2.setLastName("Jones");
        raidersRB2.setPosition("RB");

        Player raidersWR1 = new Player();
        raidersWR1.setFirstName("Michael");
        raidersWR1.setLastName("Crabtree");
        raidersWR1.setPosition("WR");

        Player raidersWR2 = new Player();
        raidersWR2.setFirstName("Amari");
        raidersWR2.setLastName("Cooper");
        raidersWR2.setPosition("WR");

        //Broncos
        Player broncosQB = new Player();
        broncosQB.setFirstName("Payton");
        broncosQB.setLastName("Manning");
        broncosQB.setPosition("QB");

        Player broncosRB1 = new Player();
        broncosRB1.setFirstName("Ronnie");
        broncosRB1.setLastName("Hillman");
        broncosRB1.setPosition("RB");

        Player broncosRB2 = new Player();
        broncosRB2.setFirstName("C.J.");
        broncosRB2.setLastName("Anderson");
        broncosRB2.setPosition("RB");

        Player broncosWR1 = new Player();
        broncosWR1.setFirstName("Demaryius");
        broncosWR1.setLastName("Thomas");
        broncosWR1.setPosition("WR");

        Player broncosWR2 = new Player();
        broncosWR2.setFirstName("Emmanual");
        broncosWR2.setLastName("Sanders");
        broncosWR2.setPosition("WR");

        //Cheifs
        Player cheifsQB = new Player();
        cheifsQB.setFirstName("Alex");
        cheifsQB.setLastName("Smith");
        cheifsQB.setPosition("QB");

        Player cheifsRB1 = new Player();
        cheifsRB1.setFirstName("Charcandrick");
        cheifsRB1.setLastName("West");
        cheifsRB1.setPosition("RB");

        Player cheifsRB2 = new Player();
        cheifsRB2.setFirstName("Spencer");
        cheifsRB2.setLastName("Ware");
        cheifsRB2.setPosition("RB");

        Player cheifsWR1 = new Player();
        cheifsWR1.setFirstName("Jeremy");
        cheifsWR1.setLastName("Maclin");
        cheifsWR1.setPosition("WR");

        Player cheifsWR2 = new Player();
        cheifsWR2.setFirstName("Albert");
        cheifsWR2.setLastName("Wilson");
        cheifsWR2.setPosition("WR");

        //Chargers
        Player chargersQB = new Player();
        chargersQB.setFirstName("Phillip"); //todo, spelling
        chargersQB.setLastName("Rivers");
        chargersQB.setPosition("QB");

        Player chargersRB1 = new Player();
        chargersRB1.setFirstName("Melvin");
        chargersRB1.setLastName("Gordon");
        chargersRB1.setPosition("RB");

        Player chargersRB2 = new Player();
        chargersRB2.setFirstName("Danny");
        chargersRB2.setLastName("Woodhead");
        chargersRB2.setPosition("RB");

        Player chargersWR1 = new Player();
        chargersWR1.setFirstName("Keenan");
        chargersWR1.setLastName("Allen");
        chargersWR1.setPosition("WR");

        Player chargersWR2 = new Player();
        chargersWR2.setFirstName("Malcom");
        chargersWR2.setLastName("Floyd");
        chargersWR2.setPosition("WR");

        raiders.setPlayers(Arrays.asList(new Player[]
        {
            raidersQB, raidersRB1, raidersRB2, raidersWR1, raidersWR2
        }));

        broncos.setPlayers(Arrays.asList(new Player[]
        {
            broncosQB, broncosRB1, broncosRB2, broncosWR1, broncosWR2
        }));

        cheifs.setPlayers(Arrays.asList(new Player[]
        {
            cheifsQB, cheifsRB1, cheifsRB2, cheifsWR1, cheifsWR2
        }));

        chargers.setPlayers(Arrays.asList(new Player[]
        {
            chargersQB, chargersRB1, chargersRB2, chargersWR1, chargersWR2
        }));

        //AFC EAST
        //Patriots
        Player patriotsQB = new Player();
        patriotsQB.setFirstName("Tom");
        patriotsQB.setLastName("Brady");

        Player patriotsRB1 = new Player();
        patriotsRB1.setFirstName("LeGarrette");
        patriotsRB1.setLastName("Blount");
        patriotsRB1.setPosition("RB");

        Player patriotsRB2 = new Player();
        patriotsRB2.setFirstName("Dion");
        patriotsRB2.setLastName("Lewis");
        patriotsRB2.setPosition("RB");

        Player patriotsWR1 = new Player();
        patriotsWR1.setFirstName("Julian");
        patriotsWR1.setLastName("Edelman");
        patriotsWR1.setPosition("WR");

        Player patriotsWR2 = new Player();
        patriotsWR2.setFirstName("Danny");
        patriotsWR2.setLastName("Amendola");
        patriotsWR2.setPosition("WR");

        //Bills
        Player billsQB = new Player();
        billsQB.setFirstName("Tyrod");
        billsQB.setLastName("Taylor");
        billsQB.setPosition("QB");

        Player billsRB1 = new Player();
        billsRB1.setFirstName("LeSean");
        billsRB1.setLastName("McCoy");
        billsRB1.setPosition("RB");

        Player billsRB2 = new Player();
        billsRB2.setFirstName("Karlos");
        billsRB2.setLastName("Williams");
        billsRB2.setPosition("RB");

        Player billsWR1 = new Player();
        billsWR1.setFirstName("Sammy");
        billsWR1.setLastName("Watkins");
        billsWR1.setPosition("WR");

        Player billsWR2 = new Player();
        billsWR2.setFirstName("Robert");
        billsWR2.setLastName("Woods");
        billsWR2.setPosition("WR");

        //Jets
        Player jetsQB = new Player();
        jetsQB.setFirstName("Ryan");
        jetsQB.setLastName("Fitzpatrick");
        jetsQB.setPosition("QB");

        Player jetsRB1 = new Player();
        jetsRB1.setFirstName("Chris");
        jetsRB1.setLastName("Ivory");
        jetsRB1.setPosition("RB");

        Player jetsRB2 = new Player();
        jetsRB2.setFirstName("Bilal");
        jetsRB2.setLastName("Powell");
        jetsRB2.setPosition("RB");

        Player jetsWR1 = new Player();
        jetsWR1.setFirstName("Brandon");
        jetsWR1.setLastName("Marshall");
        jetsWR1.setPosition("WR");

        Player jetsWR2 = new Player();
        jetsWR2.setFirstName("Eric");
        jetsWR2.setLastName("Decker");
        jetsWR2.setPosition("WR");

        //Dolphins
        Player dolphinsQB = new Player();
        dolphinsQB.setFirstName("Ryan");
        dolphinsQB.setLastName("Tannehill");
        dolphinsQB.setPosition("QB");

        Player dolphinsRB1 = new Player();
        dolphinsRB1.setFirstName("Lamar");
        dolphinsRB1.setLastName("Miller");
        dolphinsRB1.setPosition("RB");

        Player dolphinsRB2 = new Player();
        dolphinsRB2.setFirstName("Jay");
        dolphinsRB2.setLastName("Ajayi");
        dolphinsRB2.setPosition("RB");

        Player dolphinsWR1 = new Player();
        dolphinsWR1.setFirstName("Jarvis");
        dolphinsWR1.setLastName("Landry");
        dolphinsWR1.setPosition("WR");

        Player dolphinsWR2 = new Player();
        dolphinsWR2.setFirstName("Rishard");
        dolphinsWR2.setLastName("Matthews");
        dolphinsWR2.setPosition("WR");

        patriots.setPlayers(Arrays.asList(new Player[]
        {
            patriotsQB, patriotsRB1, patriotsRB2, patriotsWR1, patriotsWR2
        }));

        bills.setPlayers(Arrays.asList(new Player[]
        {
            billsQB, billsRB1, billsRB2, billsWR1, billsWR2
        }));

        jets.setPlayers(Arrays.asList(new Player[]
        {
            jetsQB, jetsRB1, jetsRB2, jetsWR1, jetsWR2
        }));

        dolphins.setPlayers(Arrays.asList(new Player[]
        {
            dolphinsQB, dolphinsRB1, dolphinsRB2, dolphinsWR1, dolphinsWR2
        }));

        //AFC SOUTH
        //Texans
        Player texansQB = new Player();
        texansQB.setFirstName("Brian");
        texansQB.setLastName("Hoyer");
        texansQB.setPosition("QB");

        Player texansRB1 = new Player();
        texansRB1.setFirstName("Alfred");
        texansRB1.setLastName("Blue");
        texansRB1.setPosition("RB");

        Player texansRB2 = new Player();
        texansRB2.setFirstName("Chris");
        texansRB2.setLastName("Polk");
        texansRB2.setPosition("RB");

        Player texansWR1 = new Player();
        texansWR1.setFirstName("DeAndre");
        texansWR1.setLastName("Hopkins");
        texansWR1.setPosition("WR");

        Player texansWR2 = new Player();
        texansWR2.setFirstName("Nate");
        texansWR2.setLastName("Washington");
        texansWR2.setPosition("WR");

        //Colts
        Player coltsQB = new Player();
        coltsQB.setFirstName("Andrew");
        coltsQB.setLastName("Luck");
        coltsQB.setPosition("QB");

        Player coltsRB1 = new Player();
        coltsRB1.setFirstName("Frank");
        coltsRB1.setLastName("Gore");
        coltsRB1.setPosition("RB");

        Player coltsRB2 = new Player();
        coltsRB2.setFirstName("Ahmad");
        coltsRB2.setLastName("Bradshaw");
        coltsRB2.setPosition("RB");

        Player coltsWR1 = new Player();
        coltsWR1.setFirstName("T.Y.");
        coltsWR1.setLastName("Hilton");
        coltsWR1.setPosition("WR");

        Player coltsWR2 = new Player();
        coltsWR2.setFirstName("Donte");
        coltsWR2.setLastName("Moncrief");
        coltsWR2.setPosition("WR");

        //Titans
        Player titansQB = new Player();
        titansQB.setFirstName("Marcus");
        titansQB.setLastName("Mariota");
        titansQB.setPosition("QB");

        Player titansRB1 = new Player();
        titansRB1.setFirstName("Antonio");
        titansRB1.setLastName("Andrews");
        titansRB1.setPosition("RB");

        Player titansRB2 = new Player();
        titansRB2.setFirstName("Dexter");
        titansRB2.setLastName("McCluster");
        titansRB2.setPosition("RB");

        Player titansWR1 = new Player();
        titansWR1.setFirstName("Doral");
        titansWR1.setLastName("Green-Beckham");
        titansWR1.setPosition("WR");

        Player titansWR2 = new Player();
        titansWR2.setFirstName("Harry");
        titansWR2.setLastName("Douglas");
        titansWR2.setPosition("WR");

        //Jags
        Player jaguarsQB = new Player();
        jaguarsQB.setFirstName("Blake");
        jaguarsQB.setLastName("Bortles");
        jaguarsQB.setPosition("QB");

        Player jaguarsRB1 = new Player();
        jaguarsRB1.setFirstName("T.J.");
        jaguarsRB1.setLastName("Yeldon");
        jaguarsRB1.setPosition("RB");

        Player jaguarsRB2 = new Player();
        jaguarsRB2.setFirstName("Denard");
        jaguarsRB2.setLastName("Robinson");
        jaguarsRB2.setPosition("RB");

        Player jaguarsWR1 = new Player();
        jaguarsWR1.setFirstName("Allen");
        jaguarsWR1.setLastName("Robinson");
        jaguarsWR1.setPosition("WR");

        Player jaguarsWR2 = new Player();
        jaguarsWR2.setFirstName("Allen");
        jaguarsWR2.setLastName("Hurns");
        jaguarsWR2.setPosition("WR");

        texans.setPlayers(Arrays.asList(new Player[]
        {
            texansQB, texansRB1, texansRB2, texansWR1, texansWR2
        }));

        colts.setPlayers(Arrays.asList(new Player[]
        {
            coltsQB, coltsRB1, coltsRB2, coltsWR1, coltsWR2
        }));

        titans.setPlayers(Arrays.asList(new Player[]
        {
            titansQB, titansRB1, titansRB2, titansWR1, titansWR2
        }));

        jaguars.setPlayers(Arrays.asList(new Player[]
        {
            jaguarsQB, jaguarsRB1, jaguarsRB2, jaguarsWR1, jaguarsWR2
        }));

        //AFC NORTH
        //Steelers
        Player steelersQB = new Player();
        steelersQB.setFirstName("Ben");
        steelersQB.setLastName("Roethlisberger");
        steelersQB.setPosition("QB");

        Player steelersRB1 = new Player();
        steelersRB1.setFirstName("DeAngelo");
        steelersRB1.setLastName("Williams");
        steelersRB1.setPosition("RB");

        Player steelersRB2 = new Player();
        steelersRB2.setFirstName("Le'Veon");
        steelersRB2.setLastName("Bell");
        steelersRB2.setPosition("RB");

        Player steelersWR1 = new Player();
        steelersWR1.setFirstName("Antonio");
        steelersWR1.setLastName("Brown");
        steelersWR1.setPosition("WR");

        Player steelersWR2 = new Player();
        steelersWR2.setFirstName("Martavis");
        steelersWR2.setLastName("Bryant");
        steelersWR2.setPosition("WR");

        //Bengals
        Player bengalsQB = new Player();
        bengalsQB.setFirstName("Andy");
        bengalsQB.setLastName("Dalton");
        bengalsQB.setPosition("QB");

        Player bengalsRB1 = new Player();
        bengalsRB1.setFirstName("Jeremy");
        bengalsRB1.setLastName("Hill");
        bengalsRB1.setPosition("RB");

        Player bengalsRB2 = new Player();
        bengalsRB2.setFirstName("Giovani");
        bengalsRB2.setLastName("Bernard");
        bengalsRB2.setPosition("RB");

        Player bengalsWR1 = new Player();
        bengalsWR1.setFirstName("A.J.");
        bengalsWR1.setLastName("Green");
        bengalsWR1.setPosition("WR");

        Player bengalsWR2 = new Player();
        bengalsWR2.setFirstName("Marvin");
        bengalsWR2.setLastName("Jones");
        bengalsWR2.setPosition("WR");

        //Browns
        Player brownsQB = new Player();
        brownsQB.setFirstName("Josh");
        brownsQB.setLastName("McCown");
        brownsQB.setPosition("QB");

        Player brownsRB1 = new Player();
        brownsRB1.setFirstName("Isaiah");
        brownsRB1.setLastName("Crowell");
        brownsRB1.setPosition("RB");

        Player brownsRB2 = new Player();
        brownsRB2.setFirstName("Duke");
        brownsRB2.setLastName("Johnson Jr.");
        brownsRB2.setPosition("RB");

        Player brownsWR1 = new Player();
        brownsWR1.setFirstName("Travis");
        brownsWR1.setLastName("Benjamin");
        brownsWR1.setPosition("WR");

        Player brownsWR2 = new Player();
        brownsWR2.setFirstName("Brian");
        brownsWR2.setLastName("Hartline");
        brownsWR2.setPosition("WR");

        //Ravens
        Player ravensQB = new Player();
        ravensQB.setFirstName("Joe");
        ravensQB.setLastName("Flacco");
        ravensQB.setPosition("QB");

        Player ravensRB1 = new Player();
        ravensRB1.setFirstName("Justin");
        ravensRB1.setLastName("Forsett");
        ravensRB1.setPosition("RB");

        Player ravensRB2 = new Player();
        ravensRB2.setFirstName("Javorius");
        ravensRB2.setLastName("Allen");
        ravensRB2.setPosition("RB");

        Player ravensWR1 = new Player();
        ravensWR1.setFirstName("Kamar");
        ravensWR1.setLastName("Allen");
        ravensWR1.setPosition("WR");

        Player ravensWR2 = new Player();
        ravensWR2.setFirstName("Steve");
        ravensWR2.setLastName("Smith Sr.");
        ravensWR2.setPosition("WR");

        steelers.setPlayers(Arrays.asList(new Player[]
        {
            steelersQB, steelersRB1, steelersRB2, steelersWR1, steelersWR2
        }));

        bengals.setPlayers(Arrays.asList(new Player[]
        {
            bengalsQB, bengalsRB1, bengalsRB2, bengalsWR1, bengalsWR2
        }));

        browns.setPlayers(Arrays.asList(new Player[]
        {
            brownsQB, brownsRB1, brownsRB2, brownsWR1, brownsWR2
        }));

        ravens.setPlayers(Arrays.asList(new Player[]
        {
            ravensQB, ravensRB1, ravensRB2, ravensWR1, ravensWR2
        }));

        //NFC WEST
        //Seahawks
        Player seahawksQB = new Player();
        seahawksQB.setFirstName("Russell");
        seahawksQB.setLastName("Wilson");
        seahawksQB.setPosition("QB");

        Player seahawksRB1 = new Player();
        seahawksRB1.setFirstName("Thomas");
        seahawksRB1.setLastName("Rawls");
        seahawksRB1.setPosition("RB");

        Player seahawksRB2 = new Player();
        seahawksRB2.setFirstName("Marshawn");
        seahawksRB2.setLastName("Lynch");
        seahawksRB2.setPosition("RB");

        Player seahawksWR1 = new Player();
        seahawksWR1.setFirstName("Doug");
        seahawksWR1.setLastName("Baldwin");
        seahawksWR1.setPosition("WR");

        Player seahawksWR2 = new Player();
        seahawksWR2.setFirstName("Jermaine");
        seahawksWR2.setLastName("Kearse");
        seahawksWR2.setPosition("WR");

        //Cardinals
        Player cardinalsQB = new Player();
        cardinalsQB.setFirstName("Carson");
        cardinalsQB.setLastName("Palmer");
        cardinalsQB.setPosition("QB");

        Player cardinalsRB1 = new Player();
        cardinalsRB1.setFirstName("Chris");
        cardinalsRB1.setLastName("Johnson");
        cardinalsRB1.setPosition("RB");

        Player cardinalsRB2 = new Player();
        cardinalsRB2.setFirstName("David");
        cardinalsRB2.setLastName("Johnson");
        cardinalsRB2.setPosition("RB");

        Player cardinalsWR1 = new Player();
        cardinalsWR1.setFirstName("Larry");
        cardinalsWR1.setLastName("Fitzgerald");
        cardinalsWR1.setPosition("WR");

        Player cardinalsWR2 = new Player();
        cardinalsWR2.setFirstName("John");
        cardinalsWR2.setLastName("Brown");
        cardinalsWR2.setPosition("WR");

        //Rams
        Player ramsQB = new Player();
        ramsQB.setFirstName("Nick");
        ramsQB.setLastName("Foles");
        ramsQB.setPosition("QB");

        Player ramsRB1 = new Player();
        ramsRB1.setFirstName("Todd");
        ramsRB1.setLastName("Gurley");
        ramsRB1.setPosition("RB");

        Player ramsRB2 = new Player();
        ramsRB2.setFirstName("Tavon");
        ramsRB2.setLastName("Austin");
        ramsRB2.setPosition("RB");

        Player ramsWR1 = new Player();
        ramsWR1.setFirstName("Kenny");
        ramsWR1.setLastName("Britt");
        ramsWR1.setPosition("WR");

        Player ramsWR2 = new Player();
        ramsWR2.setFirstName("Benjamin");
        ramsWR2.setLastName("Cunningham");
        ramsWR2.setPosition("WR");

        //49ers
        Player ninersQB = new Player();
        ninersQB.setFirstName("Blaine");
        ninersQB.setLastName("Gabbert");
        ninersQB.setPosition("QB");

        Player ninersRB1 = new Player();
        ninersRB1.setFirstName("Carlos");
        ninersRB1.setLastName("Hyde");
        ninersRB1.setPosition("RB");

        Player ninersRB2 = new Player();
        ninersRB2.setFirstName("Shaun");
        ninersRB2.setLastName("Draughn");
        ninersRB2.setPosition("RB");

        Player ninersWR1 = new Player();
        ninersWR1.setFirstName("Anquan");
        ninersWR1.setLastName("Boldin");
        ninersWR1.setPosition("WR");

        Player ninersWR2 = new Player();
        ninersWR2.setFirstName("Torrey");
        ninersWR2.setLastName("Smith");
        ninersWR2.setPosition("WR");

        seahawks.setPlayers(Arrays.asList(new Player[]
        {
            seahawksQB, seahawksRB1, seahawksRB2, seahawksWR1, seahawksWR2
        }));

        cardinals.setPlayers(Arrays.asList(new Player[]
        {
            cardinalsQB, cardinalsRB1, cardinalsRB2, cardinalsWR1, cardinalsWR2
        }));

        rams.setPlayers(Arrays.asList(new Player[]
        {
            ramsQB, ramsRB1, ramsRB2, ramsWR1, ramsWR2
        }));

        niners.setPlayers(Arrays.asList(new Player[]
        {
            ninersQB, ninersRB1, ninersRB2, ninersWR1, ninersWR2
        }));

        //NFC SOUTH
        //Panthers
        Player panthersQB = new Player();
        panthersQB.setFirstName("Cam");
        panthersQB.setLastName("Newton");
        panthersQB.setPosition("QB");

        Player panthersRB1 = new Player();
        panthersRB1.setFirstName("Jonathan");
        panthersRB1.setLastName("Stewart");
        panthersRB1.setPosition("RB");

        Player panthersRB2 = new Player();
        panthersRB2.setFirstName("Mike");
        panthersRB2.setLastName("Tolbert");
        panthersRB2.setPosition("RB");

        Player panthersWR1 = new Player();
        panthersWR1.setFirstName("Ted");
        panthersWR1.setLastName("Ginn Jr.");
        panthersWR1.setPosition("WR");

        Player panthersWR2 = new Player();
        panthersWR2.setFirstName("Jerricho");
        panthersWR2.setLastName("Cotchery");
        panthersWR2.setPosition("WR");

        //Falcons
        Player falconsQB = new Player();
        falconsQB.setFirstName("Matt");
        falconsQB.setLastName("Ryan");
        falconsQB.setPosition("QB");

        Player falconsRB1 = new Player();
        falconsRB1.setFirstName("Devonta");
        falconsRB1.setLastName("Freeman");
        falconsRB1.setPosition("RB");

        Player falconsRB2 = new Player();
        falconsRB2.setFirstName("Tevin");
        falconsRB2.setLastName("Coleman");
        falconsRB2.setPosition("RB");

        Player falconsWR1 = new Player();
        falconsWR1.setFirstName("Julio");
        falconsWR1.setLastName("Jones");
        falconsWR1.setPosition("WR");

        Player falconsWR2 = new Player();
        falconsWR2.setFirstName("Roddy");
        falconsWR2.setLastName("White");
        falconsWR2.setPosition("WR");

        //Saints
        Player saintsQB = new Player();
        saintsQB.setFirstName("Drew");
        saintsQB.setLastName("Brees");
        saintsQB.setPosition("QB");

        Player saintsRB1 = new Player();
        saintsRB1.setFirstName("Mark");
        saintsRB1.setLastName("Ingram");
        saintsRB1.setPosition("RB");

        Player saintsRB2 = new Player();
        saintsRB2.setFirstName("Tim");
        saintsRB2.setLastName("Hightower");
        saintsRB2.setPosition("RB");

        Player saintsWR1 = new Player();
        saintsWR1.setFirstName("Brandin");
        saintsWR1.setLastName("Cooks");
        saintsWR1.setPosition("WR");

        Player saintsWR2 = new Player();
        saintsWR2.setFirstName("Willie");
        saintsWR2.setLastName("Snead");
        saintsWR2.setPosition("WR");

        //Buccaneers
        Player buccaneersQB = new Player();
        buccaneersQB.setFirstName("Jameis");
        buccaneersQB.setLastName("Winston");
        buccaneersQB.setPosition("QB");

        Player buccaneersRB1 = new Player();
        buccaneersRB1.setFirstName("Doug");
        buccaneersRB1.setLastName("Martin");
        buccaneersRB1.setPosition("RB");

        Player buccaneersRB2 = new Player();
        buccaneersRB2.setFirstName("Charles");
        buccaneersRB2.setLastName("Sims");
        buccaneersRB2.setPosition("RB");

        Player buccaneersWR1 = new Player();
        buccaneersWR1.setFirstName("Mike");
        buccaneersWR1.setLastName("Evans");
        buccaneersWR1.setPosition("WR");

        Player buccaneersWR2 = new Player();
        buccaneersWR2.setFirstName("Vincent");
        buccaneersWR2.setLastName("Jackson");
        buccaneersWR2.setPosition("WR");

        panthers.setPlayers(Arrays.asList(new Player[]
        {
            panthersQB, panthersRB1, panthersRB2, panthersWR1, panthersWR2
        }));

        falcons.setPlayers(Arrays.asList(new Player[]
        {
            falconsQB, falconsRB1, falconsRB2, falconsWR1, falconsWR2
        }));

        saints.setPlayers(Arrays.asList(new Player[]
        {
            saintsQB, saintsRB1, saintsRB2, saintsWR1, saintsWR2
        }));

        buccaneers.setPlayers(Arrays.asList(new Player[]
        {
            buccaneersQB, buccaneersRB1, buccaneersRB2, buccaneersWR1, buccaneersWR2
        }));

        //NFC EAST
        //Cowboys
        Player cowboysQB = new Player();
        cowboysQB.setFirstName("Matt");
        cowboysQB.setLastName("Cassel");
        cowboysQB.setPosition("QB");

        Player cowboysRB1 = new Player();
        cowboysRB1.setFirstName("Darren");
        cowboysRB1.setLastName("McFadden");
        cowboysRB1.setPosition("RB");

        Player cowboysRB2 = new Player();
        cowboysRB2.setFirstName("Joseph");
        cowboysRB2.setLastName("Randle");
        cowboysRB2.setPosition("RB");

        Player cowboysWR1 = new Player();
        cowboysWR1.setFirstName("Terrance");
        cowboysWR1.setLastName("Williams");
        cowboysWR1.setPosition("WR");

        Player cowboysWR2 = new Player();
        cowboysWR2.setFirstName("Cole");
        cowboysWR2.setLastName("Beasley");
        cowboysWR2.setPosition("WR");

        //Eagles
        Player eaglesQB = new Player();
        eaglesQB.setFirstName("Sam");
        eaglesQB.setLastName("Bradford");
        eaglesQB.setPosition("QB");

        Player eaglesRB1 = new Player();
        eaglesRB1.setFirstName("DeMarco");
        eaglesRB1.setLastName("Murray");
        eaglesRB1.setPosition("RB");

        Player eaglesRB2 = new Player();
        eaglesRB2.setFirstName("Ryan");
        eaglesRB2.setLastName("Matthews");
        eaglesRB2.setPosition("RB");

        Player eaglesWR1 = new Player();
        eaglesWR1.setFirstName("Jordan");
        eaglesWR1.setLastName("Matthews");
        eaglesWR1.setPosition("WR");

        Player eaglesWR2 = new Player();
        eaglesWR2.setFirstName("Riley");
        eaglesWR2.setLastName("Cooper");
        eaglesWR2.setPosition("WR");

        //Giants
        Player giantsQB = new Player();
        giantsQB.setFirstName("Eli");
        giantsQB.setLastName("Manning");
        giantsQB.setPosition("QB");

        Player giantsRB1 = new Player();
        giantsRB1.setFirstName("Rashad");
        giantsRB1.setLastName("Jennings");
        giantsRB1.setPosition("RB");

        Player giantsRB2 = new Player();
        giantsRB2.setFirstName("Shane");
        giantsRB2.setLastName("Vereen");
        giantsRB2.setPosition("RB");

        Player giantsWR1 = new Player();
        giantsWR1.setFirstName("Odell");
        giantsWR1.setLastName("Beckham Jr.");
        giantsWR1.setPosition("WR");

        Player giantsWR2 = new Player();
        giantsWR2.setFirstName("Ruben");
        giantsWR2.setLastName("Randle");
        giantsWR2.setPosition("WR");

        //Redskins
        Player redskinsQB = new Player();
        redskinsQB.setFirstName("Kirk");
        redskinsQB.setLastName("Cousins");
        redskinsQB.setPosition("QB");

        Player redskinsRB1 = new Player();
        redskinsRB1.setFirstName("Alfred");
        redskinsRB1.setLastName("Morris");
        redskinsRB1.setPosition("RB");

        Player redskinsRB2 = new Player();
        redskinsRB2.setFirstName("Matt");
        redskinsRB2.setLastName("Jones");
        redskinsRB2.setPosition("RB");

        Player redskinsWR1 = new Player();
        redskinsWR1.setFirstName("Pierre");
        redskinsWR1.setLastName("Garcon");
        redskinsWR1.setPosition("WR");

        Player redskinsWR2 = new Player();
        redskinsWR2.setFirstName("Jamison");
        redskinsWR2.setLastName("Crowder");
        redskinsWR2.setPosition("WR");

        cowboys.setPlayers(Arrays.asList(new Player[]
        {
            cowboysQB, cowboysRB1, cowboysRB2, cowboysWR1, cowboysWR2
        }));

        eagles.setPlayers(Arrays.asList(new Player[]
        {
            eaglesQB, eaglesRB1, eaglesRB2, eaglesWR1, eaglesWR2
        }));

        giants.setPlayers(Arrays.asList(new Player[]
        {
            giantsQB, giantsRB1, giantsRB2, giantsWR1, giantsWR2
        }));

        redskins.setPlayers(Arrays.asList(new Player[]
        {
            redskinsQB, redskinsRB1, redskinsRB2, redskinsWR1, redskinsWR2
        }));

        //NFC NORT
        //Bears
        Player bearsQB = new Player();
        bearsQB.setFirstName("Jay");
        bearsQB.setLastName("Cutler");
        bearsQB.setPosition("QB");

        Player bearsRB1 = new Player();
        bearsRB1.setFirstName("Matt");
        bearsRB1.setLastName("Forte");
        bearsRB1.setPosition("RB");

        Player bearsRB2 = new Player();
        bearsRB2.setFirstName("Jeremy");
        bearsRB2.setLastName("Langford");
        bearsRB2.setPosition("RB");

        Player bearsWR1 = new Player();
        bearsWR1.setFirstName("Alson");
        bearsWR1.setLastName("Jeffery");
        bearsWR1.setPosition("WR");

        Player bearsWR2 = new Player();
        bearsWR2.setFirstName("Marquess");
        bearsWR2.setLastName("Wilson");
        bearsWR2.setPosition("WR");

        //Lions
        Player lionsQB = new Player();
        lionsQB.setFirstName("Matthew");
        lionsQB.setLastName("Stafford");
        lionsQB.setPosition("QB");

        Player lionsRB1 = new Player();
        lionsRB1.setFirstName("Ameer");
        lionsRB1.setLastName("Abdullah");
        lionsRB1.setPosition("RB");

        Player lionsRB2 = new Player();
        lionsRB2.setFirstName("Joique");
        lionsRB2.setLastName("Bell");
        lionsRB2.setPosition("RB");

        Player lionsWR1 = new Player();
        lionsWR1.setFirstName("Calvin");
        lionsWR1.setLastName("Johnson");
        lionsWR1.setPosition("WR");

        Player lionsWR2 = new Player();
        lionsWR2.setFirstName("Golden");
        lionsWR2.setLastName("Tate");
        lionsWR2.setPosition("WR");

        //Packers
        Player packersQB = new Player();
        packersQB.setFirstName("Aaron");
        packersQB.setLastName("Rodgers");
        packersQB.setPosition("QB");

        Player packersRB1 = new Player();
        packersRB1.setFirstName("Eddie");
        packersRB1.setLastName("Lacy");
        packersRB1.setPosition("RB");

        Player packersRB2 = new Player();
        packersRB2.setFirstName("James");
        packersRB2.setLastName("Starks");
        packersRB2.setPosition("RB");

        Player packersWR1 = new Player();
        packersWR1.setFirstName("James");
        packersWR1.setLastName("Jones");
        packersWR1.setPosition("WR");

        Player packersWR2 = new Player();
        packersWR2.setFirstName("Randall");
        packersWR2.setLastName("Cobb");
        packersWR2.setPosition("WR");

        //Vikings
        Player vikingsQB = new Player();
        vikingsQB.setFirstName("Teddy");
        vikingsQB.setLastName("Bridgewater");
        vikingsQB.setPosition("QB");

        Player vikingsRB1 = new Player();
        vikingsRB1.setFirstName("Adrian");
        vikingsRB1.setLastName("Peterson");
        vikingsRB1.setPosition("RB");

        Player vikingsRB2 = new Player();
        vikingsRB2.setFirstName("Jerick");
        vikingsRB2.setLastName("McKinnon");
        vikingsRB2.setPosition("RB");

        Player vikingsWR1 = new Player();
        vikingsWR1.setFirstName("Stefon");
        vikingsWR1.setLastName("Diggs");
        vikingsWR1.setPosition("WR");

        Player vikingsWR2 = new Player();
        vikingsWR2.setFirstName("Kyle");
        vikingsWR2.setLastName("Rudolph");
        vikingsWR2.setPosition("WR");

        bears.setPlayers(Arrays.asList(new Player[]
        {
            bearsQB, bearsRB1, bearsRB2, bearsWR1, bearsWR2
        }));

        lions.setPlayers(Arrays.asList(new Player[]
        {
            lionsQB, lionsRB1, lionsRB2, lionsWR1, lionsWR2
        }));

        packers.setPlayers(Arrays.asList(new Player[]
        {
            packersQB, packersRB1, packersRB2, packersWR1, packersWR2
        }));

        vikings.setPlayers(Arrays.asList(new Player[]
        {
            vikingsQB, vikingsRB1, vikingsRB2, vikingsWR1, vikingsWR2
        }));

        //add stats
        //Raiders
        //Carr
        Stats raidersQBStats = new Stats();
        raidersQBStats.setSeason(season2015);
        raidersQBStats.setPlayer(raidersQB);
        raidersQBStats.setPassAttempts(573);
        raidersQBStats.setPassCompletions(350);
        raidersQBStats.setPassingYards(3987);
        raidersQBStats.setPassingTouchdowns(7);
        raidersQBStats.setReceptions(0);
        raidersQBStats.setReceivingYards(0);
        raidersQBStats.setReceivingTouchdowns(0);
        raidersQBStats.setRushingAttempts(33);
        raidersQBStats.setRushingYards(138);
        raidersQBStats.setRushingTouchdowns(0);
        raidersQB.getStats().add(raidersQBStats);

        //Murray
        Stats raidersRB1Stats = new Stats();
        raidersRB1Stats.setSeason(season2015);
        raidersRB1Stats.setPlayer(raidersRB1);
        raidersRB1Stats.setPassAttempts(0);
        raidersRB1Stats.setPassCompletions(0);
        raidersRB1Stats.setPassingYards(0);
        raidersRB1Stats.setPassingTouchdowns(0);
        raidersRB1Stats.setReceptions(41);
        raidersRB1Stats.setReceivingYards(232);
        raidersRB1Stats.setReceivingTouchdowns(0);
        raidersRB1Stats.setRushingAttempts(266);
        raidersRB1Stats.setRushingYards(1066);
        raidersRB1Stats.setRushingTouchdowns(6);
        raidersRB1.getStats().add(raidersRB1Stats);

        //Jones
        Stats raidersRB2Stats = new Stats();
        raidersRB2Stats.setSeason(season2015);
        raidersRB2Stats.setPlayer(raidersRB1);
        raidersRB2Stats.setPassAttempts(0);
        raidersRB2Stats.setPassCompletions(0);
        raidersRB2Stats.setPassingYards(0);
        raidersRB2Stats.setPassingTouchdowns(0);
        raidersRB2Stats.setReceptions(41);
        raidersRB2Stats.setReceivingYards(106);
        raidersRB2Stats.setReceivingTouchdowns(1);
        raidersRB2Stats.setRushingAttempts(16);
        raidersRB2Stats.setRushingYards(74);
        raidersRB2Stats.setRushingTouchdowns(6);
        raidersRB2.getStats().add(raidersRB2Stats);

        //Crabtree
        Stats raidersWR1Stats = new Stats();
        raidersWR1Stats.setSeason(season2015);
        raidersWR1Stats.setPlayer(raidersWR1);
        raidersWR1Stats.setPassAttempts(0);
        raidersWR1Stats.setPassCompletions(0);
        raidersWR1Stats.setPassingYards(0);
        raidersWR1Stats.setPassingTouchdowns(0);
        raidersWR1Stats.setReceptions(85);
        raidersWR1Stats.setReceivingYards(922);
        raidersWR1Stats.setReceivingTouchdowns(9);
        raidersWR1Stats.setRushingAttempts(0);
        raidersWR1Stats.setRushingYards(0);
        raidersWR1Stats.setRushingTouchdowns(0);
        raidersWR1.getStats().add(raidersWR1Stats);

        //Cooper
        Stats raidersWR2Stats = new Stats();
        raidersWR2Stats.setSeason(season2015);
        raidersWR2Stats.setPlayer(raidersWR1);
        raidersWR2Stats.setPassAttempts(0);
        raidersWR2Stats.setPassCompletions(0);
        raidersWR2Stats.setPassingYards(0);
        raidersWR2Stats.setPassingTouchdowns(0);
        raidersWR2Stats.setReceptions(72);
        raidersWR2Stats.setReceivingYards(1070);
        raidersWR2Stats.setReceivingTouchdowns(6);
        raidersWR2Stats.setRushingAttempts(3);
        raidersWR2Stats.setRushingYards(-3);
        raidersWR2Stats.setRushingTouchdowns(0);
        raidersWR2.getStats().add(raidersWR2Stats);

        //Broncos
        //Manning
        Stats broncosQBStats = new Stats();
        broncosQBStats.setSeason(season2015);
        broncosQBStats.setPlayer(broncosQB);
        broncosQBStats.setPassAttempts(331);
        broncosQBStats.setPassCompletions(198);
        broncosQBStats.setPassingYards(2249);
        broncosQBStats.setPassingTouchdowns(9);
        broncosQBStats.setReceptions(0);
        broncosQBStats.setRushingAttempts(6);
        broncosQBStats.setRushingYards(-6);
        broncosQBStats.setRushingTouchdowns(0);
        broncosQB.getStats().add(broncosQBStats);

        //Hillman
        Stats broncosRB1Stats = new Stats();
        broncosRB1Stats.setSeason(season2015);
        broncosRB1Stats.setPlayer(broncosRB1);
        broncosRB1Stats.setPassAttempts(0);
        broncosRB1Stats.setPassCompletions(0);
        broncosRB1Stats.setPassingYards(0);
        broncosRB1Stats.setPassingTouchdowns(0);
        broncosRB1Stats.setReceptions(24);
        broncosRB1Stats.setReceivingYards(111);
        broncosRB1Stats.setReceivingTouchdowns(0);
        broncosRB1Stats.setRushingAttempts(207);
        broncosRB1Stats.setRushingYards(863);
        broncosRB1Stats.setRushingTouchdowns(7);
        broncosRB1.getStats().add(broncosRB1Stats);

        //Anderson
        Stats broncosRB2Stats = new Stats();
        broncosRB2Stats.setSeason(season2015);
        broncosRB2Stats.setPlayer(broncosRB1);
        broncosRB2Stats.setPassAttempts(0);
        broncosRB2Stats.setPassCompletions(0);
        broncosRB2Stats.setPassingYards(0);
        broncosRB2Stats.setPassingTouchdowns(0);
        broncosRB2Stats.setReceptions(25);
        broncosRB2Stats.setReceivingYards(36);
        broncosRB2Stats.setReceivingYards(183);
        broncosRB2Stats.setReceivingTouchdowns(0);
        broncosRB2Stats.setRushingAttempts(152);
        broncosRB2Stats.setRushingYards(720);
        broncosRB2Stats.setRushingTouchdowns(5);
        broncosRB2.getStats().add(broncosRB2Stats);

        //Thomas
        Stats broncosWR1Stats = new Stats();
        broncosWR1Stats.setSeason(season2015);
        broncosWR1Stats.setPlayer(broncosWR1);
        broncosWR1Stats.setPassAttempts(0);
        broncosWR1Stats.setPassCompletions(0);
        broncosWR1Stats.setPassingYards(0);
        broncosWR1Stats.setPassingTouchdowns(0);
        broncosWR1Stats.setReceptions(105);
        broncosWR1Stats.setReceivingYards(1304);
        broncosWR1Stats.setReceivingTouchdowns(6);
        broncosWR1Stats.setRushingAttempts(0);
        broncosWR1Stats.setRushingYards(0);
        broncosWR1Stats.setRushingTouchdowns(0);
        broncosWR1.getStats().add(broncosWR1Stats);

        //Sanders
        Stats broncosWR2Stats = new Stats();
        broncosWR2Stats.setSeason(season2015);
        broncosWR2Stats.setPlayer(broncosWR1);
        broncosWR2Stats.setPassAttempts(0);
        broncosWR2Stats.setPassCompletions(0);
        broncosWR2Stats.setPassingYards(0);
        broncosWR2Stats.setPassingTouchdowns(0);
        broncosWR2Stats.setReceptions(72);
        broncosWR2Stats.setReceivingYards(1135);
        broncosWR2Stats.setReceivingTouchdowns(6);
        broncosWR2Stats.setRushingAttempts(3);
        broncosWR2Stats.setRushingYards(29);
        broncosWR2Stats.setRushingTouchdowns(0);
        broncosWR2.getStats().add(broncosWR2Stats);

        //Cheifs
        //Smith
        Stats cheifsQBStats = new Stats();
        cheifsQBStats.setSeason(season2015);
        cheifsQBStats.setPlayer(cheifsQB);
        cheifsQBStats.setPassAttempts(470);
        cheifsQBStats.setPassCompletions(307);
        cheifsQBStats.setPassingYards(3486);
        cheifsQBStats.setPassingTouchdowns(20);
        cheifsQBStats.setReceptions(1);
        cheifsQBStats.setRushingAttempts(84);
        cheifsQBStats.setRushingYards(498);
        cheifsQBStats.setRushingTouchdowns(2);
        cheifsQB.getStats().add(cheifsQBStats);

        //West
        Stats cheifsRB1Stats = new Stats();
        cheifsRB1Stats.setSeason(season2015);
        cheifsRB1Stats.setPlayer(cheifsRB1);
        cheifsRB1Stats.setPassAttempts(0);
        cheifsRB1Stats.setPassCompletions(0);
        cheifsRB1Stats.setPassingYards(0);
        cheifsRB1Stats.setPassingTouchdowns(0);
        cheifsRB1Stats.setReceptions(20);
        cheifsRB1Stats.setReceivingYards(214);
        cheifsRB1Stats.setReceivingTouchdowns(1);
        cheifsRB1Stats.setRushingAttempts(160);
        cheifsRB1Stats.setRushingYards(634);
        cheifsRB1Stats.setRushingTouchdowns(4);
        cheifsRB1.getStats().add(cheifsRB1Stats);

        //Ware
        Stats cheifsRB2Stats = new Stats();
        cheifsRB2Stats.setSeason(season2015);
        cheifsRB2Stats.setPlayer(cheifsRB1);
        cheifsRB2Stats.setPassAttempts(0);
        cheifsRB2Stats.setPassCompletions(0);
        cheifsRB2Stats.setPassingYards(0);
        cheifsRB2Stats.setPassingTouchdowns(0);
        cheifsRB2Stats.setReceptions(6);
        cheifsRB2Stats.setReceivingYards(5);
        cheifsRB2Stats.setReceivingTouchdowns(1);
        cheifsRB2Stats.setRushingAttempts(72);
        cheifsRB2Stats.setRushingYards(403);
        cheifsRB2Stats.setRushingTouchdowns(6);
        cheifsRB2.getStats().add(cheifsRB2Stats);

        //Maclin
        Stats cheifsWR1Stats = new Stats();
        cheifsWR1Stats.setSeason(season2015);
        cheifsWR1Stats.setPlayer(cheifsWR1);
        cheifsWR1Stats.setPassAttempts(0);
        cheifsWR1Stats.setPassCompletions(0);
        cheifsWR1Stats.setPassingYards(0);
        cheifsWR1Stats.setPassingTouchdowns(0);
        cheifsWR1Stats.setReceptions(87);
        cheifsWR1Stats.setReceivingYards(1088);
        cheifsWR1Stats.setReceivingTouchdowns(8);
        cheifsWR1Stats.setRushingAttempts(3);
        cheifsWR1Stats.setRushingYards(14);
        cheifsWR1Stats.setRushingTouchdowns(0);
        cheifsWR1.getStats().add(cheifsWR1Stats);

        //Wilson
        Stats cheifsWR2Stats = new Stats();
        cheifsWR2Stats.setSeason(season2015);
        cheifsWR2Stats.setPlayer(cheifsWR1);
        cheifsWR2Stats.setPassAttempts(0);
        cheifsWR2Stats.setPassCompletions(0);
        cheifsWR2Stats.setPassingYards(0);
        cheifsWR2Stats.setPassingTouchdowns(0);
        cheifsWR2Stats.setReceptions(35);
        cheifsWR2Stats.setReceivingYards(451);
        cheifsWR2Stats.setReceivingTouchdowns(2);
        cheifsWR2Stats.setRushingAttempts(5);
        cheifsWR2Stats.setRushingYards(26);
        cheifsWR2Stats.setRushingTouchdowns(0);
        cheifsWR2.getStats().add(cheifsWR2Stats);

        //Chargers
        //Rivers
        Stats chargersQBStats = new Stats();
        chargersQBStats.setSeason(season2015);
        chargersQBStats.setPlayer(chargersQB);
        chargersQBStats.setPassAttempts(661);
        chargersQBStats.setPassCompletions(437);
        chargersQBStats.setPassingYards(4792);
        chargersQBStats.setPassingTouchdowns(29);
        chargersQBStats.setReceptions(0);
        chargersQBStats.setRushingAttempts(17);
        chargersQBStats.setRushingYards(28);
        chargersQBStats.setRushingTouchdowns(0);
        chargersQB.getStats().add(chargersQBStats);

        //Gordon
        Stats chargersRB1Stats = new Stats();
        chargersRB1Stats.setSeason(season2015);
        chargersRB1Stats.setPlayer(chargersRB1);
        chargersRB1Stats.setPassAttempts(0);
        chargersRB1Stats.setPassCompletions(0);
        chargersRB1Stats.setPassingYards(0);
        chargersRB1Stats.setPassingTouchdowns(0);
        chargersRB1Stats.setReceptions(33);
        chargersRB1Stats.setReceivingYards(37);
        chargersRB1Stats.setReceivingTouchdowns(0);
        chargersRB1Stats.setRushingAttempts(184);
        chargersRB1Stats.setRushingYards(641);
        chargersRB1Stats.setRushingTouchdowns(0);
        chargersRB1.getStats().add(chargersRB1Stats);

        //Woodhead
        Stats chargersRB2Stats = new Stats();
        chargersRB2Stats.setSeason(season2015);
        chargersRB2Stats.setPlayer(chargersRB1);
        chargersRB2Stats.setPassAttempts(0);
        chargersRB2Stats.setPassCompletions(0);
        chargersRB2Stats.setPassingYards(0);
        chargersRB2Stats.setPassingTouchdowns(0);
        chargersRB2Stats.setReceptions(80);
        chargersRB2Stats.setReceivingYards(106);
        chargersRB2Stats.setReceivingTouchdowns(6);
        chargersRB2Stats.setRushingAttempts(98);
        chargersRB2Stats.setRushingYards(336);
        chargersRB2Stats.setRushingTouchdowns(3);
        chargersRB2.getStats().add(chargersRB2Stats);

        //Allen
        Stats chargersWR1Stats = new Stats();
        chargersWR1Stats.setSeason(season2015);
        chargersWR1Stats.setPlayer(chargersWR1);
        chargersWR1Stats.setPassAttempts(0);
        chargersWR1Stats.setPassCompletions(0);
        chargersWR1Stats.setPassingYards(0);
        chargersWR1Stats.setPassingTouchdowns(0);
        chargersWR1Stats.setReceptions(67);
        chargersWR1Stats.setReceivingYards(725);
        chargersWR1Stats.setReceivingTouchdowns(4);
        chargersWR1Stats.setRushingAttempts(0);
        chargersWR1Stats.setRushingYards(0);
        chargersWR1Stats.setRushingTouchdowns(0);
        chargersWR1.getStats().add(chargersWR1Stats);

        //Floyd
        Stats chargersWR2Stats = new Stats();
        chargersWR2Stats.setSeason(season2015);
        chargersWR2Stats.setPlayer(chargersWR1);
        chargersWR2Stats.setPassAttempts(0);
        chargersWR2Stats.setPassCompletions(0);
        chargersWR2Stats.setPassingYards(0);
        chargersWR2Stats.setPassingTouchdowns(0);
        chargersWR2Stats.setReceptions(30);
        chargersWR2Stats.setReceivingYards(561);
        chargersWR2Stats.setReceivingTouchdowns(3);
        chargersWR2Stats.setRushingAttempts(0);
        chargersWR2Stats.setRushingYards(0);
        chargersWR2Stats.setRushingTouchdowns(0);
        chargersWR2.getStats().add(chargersWR2Stats);

        //Raiders
        //Brady
        Stats patriotsQBStats = new Stats();
        patriotsQBStats.setSeason(season2015);
        patriotsQBStats.setPlayer(patriotsQB);
        patriotsQBStats.setPassAttempts(624);
        patriotsQBStats.setPassCompletions(402);
        patriotsQBStats.setPassingYards(4770);
        patriotsQBStats.setPassingTouchdowns(36);
        patriotsQBStats.setReceptions(1);
        patriotsQBStats.setRushingAttempts(34);
        patriotsQBStats.setRushingYards(53);
        patriotsQBStats.setRushingTouchdowns(3);
        patriotsQB.getStats().add(patriotsQBStats);

        //Blount
        Stats patriotsRB1Stats = new Stats();
        patriotsRB1Stats.setSeason(season2015);
        patriotsRB1Stats.setPlayer(patriotsRB1);
        patriotsRB1Stats.setPassAttempts(0);
        patriotsRB1Stats.setPassCompletions(0);
        patriotsRB1Stats.setPassingYards(0);
        patriotsRB1Stats.setPassingTouchdowns(0);
        patriotsRB1Stats.setReceptions(6);
        patriotsRB1Stats.setReceivingYards(7);
        patriotsRB1Stats.setReceivingTouchdowns(1);
        patriotsRB1Stats.setRushingAttempts(165);
        patriotsRB1Stats.setRushingYards(703);
        patriotsRB1Stats.setRushingTouchdowns(6);
        patriotsRB1.getStats().add(patriotsRB1Stats);

        //Lewis
        Stats patriotsRB2Stats = new Stats();
        patriotsRB2Stats.setSeason(season2015);
        patriotsRB2Stats.setPlayer(patriotsRB1);
        patriotsRB2Stats.setPassAttempts(0);
        patriotsRB2Stats.setPassCompletions(0);
        patriotsRB2Stats.setPassingYards(0);
        patriotsRB2Stats.setPassingTouchdowns(0);
        patriotsRB2Stats.setReceptions(36);
        patriotsRB2Stats.setReceivingYards(388);
        patriotsRB2Stats.setReceivingTouchdowns(2);
        patriotsRB2Stats.setRushingAttempts(49);
        patriotsRB2Stats.setRushingYards(234);
        patriotsRB2Stats.setRushingTouchdowns(2);
        patriotsRB2.getStats().add(patriotsRB2Stats);

        //Edelman
        Stats patriotsWR1Stats = new Stats();
        patriotsWR1Stats.setSeason(season2015);
        patriotsWR1Stats.setPlayer(patriotsWR1);
        patriotsWR1Stats.setPassAttempts(0);
        patriotsWR1Stats.setPassCompletions(0);
        patriotsWR1Stats.setPassingYards(0);
        patriotsWR1Stats.setPassingTouchdowns(0);
        patriotsWR1Stats.setReceptions(61);
        patriotsWR1Stats.setReceivingYards(692);
        patriotsWR1Stats.setReceivingTouchdowns(7);
        patriotsWR1Stats.setRushingAttempts(3);
        patriotsWR1Stats.setRushingYards(23);
        patriotsWR1Stats.setRushingTouchdowns(0);
        patriotsWR1.getStats().add(patriotsWR1Stats);

        //Amendola
        Stats patriotsWR2Stats = new Stats();
        patriotsWR2Stats.setSeason(season2015);
        patriotsWR2Stats.setPlayer(patriotsWR1);
        patriotsWR2Stats.setPassAttempts(0);
        patriotsWR2Stats.setPassCompletions(0);
        patriotsWR2Stats.setPassingYards(0);
        patriotsWR2Stats.setPassingTouchdowns(0);
        patriotsWR2Stats.setReceptions(65);
        patriotsWR2Stats.setReceivingYards(648);
        patriotsWR2Stats.setReceivingTouchdowns(3);
        patriotsWR2Stats.setRushingAttempts(2);
        patriotsWR2Stats.setRushingYards(11);
        patriotsWR2Stats.setRushingTouchdowns(0);
        patriotsWR2.getStats().add(patriotsWR2Stats);

        //Bills
        //Taylor
        Stats billsQBStats = new Stats();
        billsQBStats.setSeason(season2015);
        billsQBStats.setPlayer(billsQB);
        billsQBStats.setPassAttempts(380);
        billsQBStats.setPassCompletions(242);
        billsQBStats.setPassingYards(3035);
        billsQBStats.setPassingTouchdowns(20);
        billsQBStats.setReceptions(1);
        billsQBStats.setReceivingYards(4);
        billsQBStats.setReceivingTouchdowns(0);
        billsQBStats.setRushingAttempts(104);
        billsQBStats.setRushingYards(568);
        billsQBStats.setRushingTouchdowns(4);
        billsQB.getStats().add(billsQBStats);

        //McCoy
        Stats billsRB1Stats = new Stats();
        billsRB1Stats.setSeason(season2015);
        billsRB1Stats.setPlayer(billsRB1);
        billsRB1Stats.setPassAttempts(0);
        billsRB1Stats.setPassCompletions(0);
        billsRB1Stats.setPassingYards(0);
        billsRB1Stats.setPassingTouchdowns(0);
        billsRB1Stats.setReceptions(32);
        billsRB1Stats.setReceivingYards(292);
        billsRB1Stats.setReceivingTouchdowns(2);
        billsRB1Stats.setRushingAttempts(203);
        billsRB1Stats.setRushingYards(895);
        billsRB1Stats.setRushingTouchdowns(3);
        billsRB1.getStats().add(billsRB1Stats);

        //Williams
        Stats billsRB2Stats = new Stats();
        billsRB2Stats.setSeason(season2015);
        billsRB2Stats.setPlayer(billsRB1);
        billsRB2Stats.setPassAttempts(0);
        billsRB2Stats.setPassCompletions(0);
        billsRB2Stats.setPassingYards(0);
        billsRB2Stats.setPassingTouchdowns(0);
        billsRB2Stats.setReceptions(11);
        billsRB2Stats.setReceivingYards(14);
        billsRB2Stats.setReceivingTouchdowns(2);
        billsRB2Stats.setRushingAttempts(93);
        billsRB2Stats.setRushingYards(517);
        billsRB2Stats.setRushingTouchdowns(7);
        billsRB2.getStats().add(billsRB2Stats);

        //Watkins
        Stats billsWR1Stats = new Stats();
        billsWR1Stats.setSeason(season2015);
        billsWR1Stats.setPlayer(billsWR1);
        billsWR1Stats.setPassAttempts(0);
        billsWR1Stats.setPassCompletions(0);
        billsWR1Stats.setPassingYards(0);
        billsWR1Stats.setPassingTouchdowns(0);
        billsWR1Stats.setReceptions(60);
        billsWR1Stats.setReceivingYards(1047);
        billsWR1Stats.setReceivingTouchdowns(9);
        billsWR1Stats.setRushingAttempts(1);
        billsWR1Stats.setRushingYards(1);
        billsWR1Stats.setRushingTouchdowns(0);
        billsWR1.getStats().add(billsWR1Stats);

        //Woods
        Stats billsWR2Stats = new Stats();
        billsWR2Stats.setSeason(season2015);
        billsWR2Stats.setPlayer(billsWR1);
        billsWR2Stats.setPassAttempts(0);
        billsWR2Stats.setPassCompletions(0);
        billsWR2Stats.setPassingYards(0);
        billsWR2Stats.setPassingTouchdowns(0);
        billsWR2Stats.setReceptions(47);
        billsWR2Stats.setReceivingYards(552);
        billsWR2Stats.setReceivingTouchdowns(3);
        billsWR2Stats.setRushingAttempts(1);
        billsWR2Stats.setRushingYards(0);
        billsWR2Stats.setRushingTouchdowns(0);
        billsWR2.getStats().add(billsWR2Stats);

        //Jets
        //Fitzpatrick
        Stats jetsQBStats = new Stats();
        jetsQBStats.setSeason(season2015);
        jetsQBStats.setPlayer(jetsQB);
        jetsQBStats.setPassAttempts(562);
        jetsQBStats.setPassCompletions(335);
        jetsQBStats.setPassingYards(3905);
        jetsQBStats.setPassingTouchdowns(31);
        jetsQBStats.setReceptions(0);
        jetsQBStats.setReceivingYards(0);
        jetsQBStats.setReceivingTouchdowns(0);
        jetsQBStats.setRushingAttempts(60);
        jetsQBStats.setRushingYards(270);
        jetsQBStats.setRushingTouchdowns(2);
        jetsQB.getStats().add(jetsQBStats);

        //Ivory
        Stats jetsRB1Stats = new Stats();
        jetsRB1Stats.setSeason(season2015);
        jetsRB1Stats.setPlayer(jetsRB1);
        jetsRB1Stats.setPassAttempts(0);
        jetsRB1Stats.setPassCompletions(0);
        jetsRB1Stats.setPassingYards(0);
        jetsRB1Stats.setPassingTouchdowns(0);
        jetsRB1Stats.setReceptions(30);
        jetsRB1Stats.setReceivingYards(217);
        jetsRB1Stats.setReceivingTouchdowns(1);
        jetsRB1Stats.setRushingAttempts(247);
        jetsRB1Stats.setRushingYards(1070);
        jetsRB1Stats.setRushingTouchdowns(7);
        jetsRB1.getStats().add(jetsRB1Stats);

        //Powell
        Stats jetsRB2Stats = new Stats();
        jetsRB2Stats.setSeason(season2015);
        jetsRB2Stats.setPlayer(jetsRB1);
        jetsRB2Stats.setPassAttempts(0);
        jetsRB2Stats.setPassCompletions(0);
        jetsRB2Stats.setPassingYards(0);
        jetsRB2Stats.setPassingTouchdowns(0);
        jetsRB2Stats.setReceptions(47);
        jetsRB2Stats.setReceivingYards(388);
        jetsRB2Stats.setReceivingTouchdowns(2);
        jetsRB2Stats.setRushingAttempts(70);
        jetsRB2Stats.setRushingYards(313);
        jetsRB2Stats.setRushingTouchdowns(1);
        jetsRB2.getStats().add(jetsRB2Stats);

        //Marshall
        Stats jetsWR1Stats = new Stats();
        jetsWR1Stats.setSeason(season2015);
        jetsWR1Stats.setPlayer(jetsWR1);
        jetsWR1Stats.setPassAttempts(0);
        jetsWR1Stats.setPassCompletions(0);
        jetsWR1Stats.setPassingYards(0);
        jetsWR1Stats.setPassingTouchdowns(0);
        jetsWR1Stats.setReceptions(109);
        jetsWR1Stats.setReceivingYards(1502);
        jetsWR1Stats.setReceivingTouchdowns(14);
        jetsWR1Stats.setRushingAttempts(0);
        jetsWR1Stats.setRushingYards(0);
        jetsWR1Stats.setRushingTouchdowns(0);
        jetsWR1.getStats().add(jetsWR1Stats);

        //Decker
        Stats jetsWR2Stats = new Stats();
        jetsWR2Stats.setSeason(season2015);
        jetsWR2Stats.setPlayer(jetsWR1);
        jetsWR2Stats.setPassAttempts(0);
        jetsWR2Stats.setPassCompletions(0);
        jetsWR2Stats.setPassingYards(0);
        jetsWR2Stats.setPassingTouchdowns(0);
        jetsWR2Stats.setReceptions(80);
        jetsWR2Stats.setReceivingYards(1027);
        jetsWR2Stats.setReceivingTouchdowns(12);
        jetsWR2Stats.setRushingAttempts(0);
        jetsWR2Stats.setRushingYards(0);
        jetsWR2Stats.setRushingTouchdowns(0);
        jetsWR2.getStats().add(jetsWR2Stats);

        //Dolphins
        //Tannehill
        Stats dolphinsQBStats = new Stats();
        dolphinsQBStats.setSeason(season2015);
        dolphinsQBStats.setPlayer(dolphinsQB);
        dolphinsQBStats.setPassAttempts(586);
        dolphinsQBStats.setPassCompletions(363);
        dolphinsQBStats.setPassingYards(3987);
        dolphinsQBStats.setPassingTouchdowns(24);
        dolphinsQBStats.setReceptions(0);
        dolphinsQBStats.setReceivingYards(0);
        dolphinsQBStats.setReceivingTouchdowns(0);
        dolphinsQBStats.setRushingAttempts(32);
        dolphinsQBStats.setRushingYards(141);
        dolphinsQBStats.setRushingTouchdowns(1);
        dolphinsQB.getStats().add(dolphinsQBStats);

        //Miller
        Stats dolphinsRB1Stats = new Stats();
        dolphinsRB1Stats.setSeason(season2015);
        dolphinsRB1Stats.setPlayer(dolphinsRB1);
        dolphinsRB1Stats.setPassAttempts(0);
        dolphinsRB1Stats.setPassCompletions(0);
        dolphinsRB1Stats.setPassingYards(0);
        dolphinsRB1Stats.setPassingTouchdowns(0);
        dolphinsRB1Stats.setReceptions(47);
        dolphinsRB1Stats.setReceivingYards(397);
        dolphinsRB1Stats.setReceivingTouchdowns(2);
        dolphinsRB1Stats.setRushingAttempts(194);
        dolphinsRB1Stats.setRushingYards(872);
        dolphinsRB1Stats.setRushingTouchdowns(8);
        dolphinsRB1.getStats().add(dolphinsRB1Stats);

        //Ajayi
        Stats dolphinsRB2Stats = new Stats();
        dolphinsRB2Stats.setSeason(season2015);
        dolphinsRB2Stats.setPlayer(dolphinsRB1);
        dolphinsRB2Stats.setPassAttempts(0);
        dolphinsRB2Stats.setPassCompletions(0);
        dolphinsRB2Stats.setPassingYards(0);
        dolphinsRB2Stats.setPassingTouchdowns(0);
        dolphinsRB2Stats.setReceptions(7);
        dolphinsRB2Stats.setReceivingYards(90);
        dolphinsRB2Stats.setReceivingTouchdowns(0);
        dolphinsRB2Stats.setRushingAttempts(49);
        dolphinsRB2Stats.setRushingYards(187);
        dolphinsRB2Stats.setRushingTouchdowns(1);
        dolphinsRB2.getStats().add(dolphinsRB2Stats);

        //Landry
        Stats dolphinsWR1Stats = new Stats();
        dolphinsWR1Stats.setSeason(season2015);
        dolphinsWR1Stats.setPlayer(dolphinsWR1);
        dolphinsWR1Stats.setPassAttempts(0);
        dolphinsWR1Stats.setPassCompletions(0);
        dolphinsWR1Stats.setPassingYards(0);
        dolphinsWR1Stats.setPassingTouchdowns(0);
        dolphinsWR1Stats.setReceptions(110);
        dolphinsWR1Stats.setReceivingYards(1157);
        dolphinsWR1Stats.setReceivingTouchdowns(4);
        dolphinsWR1Stats.setRushingAttempts(18);
        dolphinsWR1Stats.setRushingYards(113);
        dolphinsWR1Stats.setRushingTouchdowns(1);
        dolphinsWR1.getStats().add(dolphinsWR1Stats);

        //Matthews
        Stats dolphinsWR2Stats = new Stats();
        dolphinsWR2Stats.setSeason(season2015);
        dolphinsWR2Stats.setPlayer(dolphinsWR1);
        dolphinsWR2Stats.setPassAttempts(0);
        dolphinsWR2Stats.setPassCompletions(0);
        dolphinsWR2Stats.setPassingYards(0);
        dolphinsWR2Stats.setPassingTouchdowns(0);
        dolphinsWR2Stats.setReceptions(43);
        dolphinsWR2Stats.setReceivingYards(662);
        dolphinsWR2Stats.setReceivingTouchdowns(4);
        dolphinsWR2Stats.setRushingAttempts(1);
        dolphinsWR2Stats.setRushingYards(4);
        dolphinsWR2Stats.setRushingTouchdowns(0);
        dolphinsWR2.getStats().add(dolphinsWR2Stats);

        //Texans
        //Hoyer
        Stats texansQBStats = new Stats();
        texansQBStats.setSeason(season2015);
        texansQBStats.setPlayer(texansQB);
        texansQBStats.setPassAttempts(369);
        texansQBStats.setPassCompletions(224);
        texansQBStats.setPassingYards(2606);
        texansQBStats.setPassingTouchdowns(19);
        texansQBStats.setReceptions(0);
        texansQBStats.setReceivingYards(0);
        texansQBStats.setReceivingTouchdowns(0);
        texansQBStats.setRushingAttempts(15);
        texansQBStats.setRushingYards(44);
        texansQBStats.setRushingTouchdowns(0);
        texansQB.getStats().add(texansQBStats);

        //Blue
        Stats texansRB1Stats = new Stats();
        texansRB1Stats.setSeason(season2015);
        texansRB1Stats.setPlayer(texansRB1);
        texansRB1Stats.setPassAttempts(0);
        texansRB1Stats.setPassCompletions(0);
        texansRB1Stats.setPassingYards(0);
        texansRB1Stats.setPassingTouchdowns(0);
        texansRB1Stats.setReceptions(15);
        texansRB1Stats.setReceivingYards(109);
        texansRB1Stats.setReceivingTouchdowns(1);
        texansRB1Stats.setRushingAttempts(183);
        texansRB1Stats.setRushingYards(698);
        texansRB1Stats.setRushingTouchdowns(2);
        texansRB1.getStats().add(texansRB1Stats);

        //Polk
        Stats texansRB2Stats = new Stats();
        texansRB2Stats.setSeason(season2015);
        texansRB2Stats.setPlayer(texansRB1);
        texansRB2Stats.setPassAttempts(0);
        texansRB2Stats.setPassCompletions(0);
        texansRB2Stats.setPassingYards(0);
        texansRB2Stats.setPassingTouchdowns(0);
        texansRB2Stats.setReceptions(16);
        texansRB2Stats.setReceivingYards(109);
        texansRB2Stats.setReceivingTouchdowns(1);
        texansRB2Stats.setRushingAttempts(99);
        texansRB2Stats.setRushingYards(334);
        texansRB2Stats.setRushingTouchdowns(1);
        texansRB2.getStats().add(texansRB2Stats);

        //Hopkins
        Stats texansWR1Stats = new Stats();
        texansWR1Stats.setSeason(season2015);
        texansWR1Stats.setPlayer(texansWR1);
        texansWR1Stats.setPassAttempts(0);
        texansWR1Stats.setPassCompletions(0);
        texansWR1Stats.setPassingYards(0);
        texansWR1Stats.setPassingTouchdowns(0);
        texansWR1Stats.setReceptions(111);
        texansWR1Stats.setReceivingYards(1521);
        texansWR1Stats.setReceivingTouchdowns(11);
        texansWR1Stats.setRushingAttempts(0);
        texansWR1Stats.setRushingYards(0);
        texansWR1Stats.setRushingTouchdowns(0);
        texansWR1.getStats().add(texansWR1Stats);

        //Washington
        Stats texansWR2Stats = new Stats();
        texansWR2Stats.setSeason(season2015);
        texansWR2Stats.setPlayer(texansWR1);
        texansWR2Stats.setPassAttempts(0);
        texansWR2Stats.setPassCompletions(0);
        texansWR2Stats.setPassingYards(0);
        texansWR2Stats.setPassingTouchdowns(0);
        texansWR2Stats.setReceptions(47);
        texansWR2Stats.setReceivingYards(658);
        texansWR2Stats.setReceivingTouchdowns(4);
        texansWR2Stats.setRushingAttempts(0);
        texansWR2Stats.setRushingYards(0);
        texansWR2Stats.setRushingTouchdowns(0);
        texansWR2.getStats().add(texansWR2Stats);

        //Colts
        //Luck
        Stats coltsQBStats = new Stats();
        coltsQBStats.setSeason(season2015);
        coltsQBStats.setPlayer(coltsQB);
        coltsQBStats.setPassAttempts(293);
        coltsQBStats.setPassCompletions(162);
        coltsQBStats.setPassingYards(1881);
        coltsQBStats.setPassingTouchdowns(15);
        coltsQBStats.setReceptions(0);
        coltsQBStats.setReceivingYards(0);
        coltsQBStats.setReceivingTouchdowns(0);
        coltsQBStats.setRushingAttempts(33);
        coltsQBStats.setRushingYards(196);
        coltsQBStats.setRushingTouchdowns(0);
        coltsQB.getStats().add(coltsQBStats);

        //Gore
        Stats coltsRB1Stats = new Stats();
        coltsRB1Stats.setSeason(season2015);
        coltsRB1Stats.setPlayer(coltsRB1);
        coltsRB1Stats.setPassAttempts(0);
        coltsRB1Stats.setPassCompletions(0);
        coltsRB1Stats.setPassingYards(0);
        coltsRB1Stats.setPassingTouchdowns(0);
        coltsRB1Stats.setReceptions(34);
        coltsRB1Stats.setReceivingYards(267);
        coltsRB1Stats.setReceivingTouchdowns(1);
        coltsRB1Stats.setRushingAttempts(260);
        coltsRB1Stats.setRushingYards(967);
        coltsRB1Stats.setRushingTouchdowns(6);
        coltsRB1.getStats().add(coltsRB1Stats);

        //Bradshaw
        Stats coltsRB2Stats = new Stats();
        coltsRB2Stats.setSeason(season2015);
        coltsRB2Stats.setPlayer(coltsRB1);
        coltsRB2Stats.setPassAttempts(0);
        coltsRB2Stats.setPassCompletions(0);
        coltsRB2Stats.setPassingYards(0);
        coltsRB2Stats.setPassingTouchdowns(0);
        coltsRB2Stats.setReceptions(0);
        coltsRB2Stats.setReceivingYards(0);
        coltsRB2Stats.setReceivingTouchdowns(0);
        coltsRB2Stats.setRushingAttempts(31);
        coltsRB2Stats.setRushingYards(85);
        coltsRB2Stats.setRushingTouchdowns(1);
        coltsRB2.getStats().add(coltsRB2Stats);

        //Hilton
        Stats coltsWR1Stats = new Stats();
        coltsWR1Stats.setSeason(season2015);
        coltsWR1Stats.setPlayer(coltsWR1);
        coltsWR1Stats.setPassAttempts(0);
        coltsWR1Stats.setPassCompletions(0);
        coltsWR1Stats.setPassingYards(0);
        coltsWR1Stats.setPassingTouchdowns(0);
        coltsWR1Stats.setReceptions(69);
        coltsWR1Stats.setReceivingYards(1124);
        coltsWR1Stats.setReceivingTouchdowns(5);
        coltsWR1Stats.setRushingAttempts(0);
        coltsWR1Stats.setRushingYards(0);
        coltsWR1Stats.setRushingTouchdowns(0);
        coltsWR1.getStats().add(coltsWR1Stats);

        //Moncrief
        Stats coltsWR2Stats = new Stats();
        coltsWR2Stats.setSeason(season2015);
        coltsWR2Stats.setPlayer(coltsWR1);
        coltsWR2Stats.setPassAttempts(0);
        coltsWR2Stats.setPassCompletions(0);
        coltsWR2Stats.setPassingYards(0);
        coltsWR2Stats.setPassingTouchdowns(0);
        coltsWR2Stats.setReceptions(47);
        coltsWR2Stats.setReceivingYards(733);
        coltsWR2Stats.setReceivingTouchdowns(4);
        coltsWR2Stats.setRushingAttempts(0);
        coltsWR2Stats.setRushingYards(0);
        coltsWR2Stats.setRushingTouchdowns(0);
        coltsWR2.getStats().add(coltsWR2Stats);

        //Steelers
        //Roethlisberger
        Stats steelersQBStats = new Stats();
        steelersQBStats.setSeason(season2015);
        steelersQBStats.setPlayer(steelersQB);
        steelersQBStats.setPassAttempts(469);
        steelersQBStats.setPassCompletions(319);
        steelersQBStats.setPassingYards(3938);
        steelersQBStats.setPassingTouchdowns(21);
        steelersQBStats.setReceptions(0);
        steelersQBStats.setReceivingYards(0);
        steelersQBStats.setReceivingTouchdowns(0);
        steelersQBStats.setRushingAttempts(15);
        steelersQBStats.setRushingYards(29);
        steelersQBStats.setRushingTouchdowns(0);
        steelersQB.getStats().add(steelersQBStats);

        //Williams
        Stats steelersRB1Stats = new Stats();
        steelersRB1Stats.setSeason(season2015);
        steelersRB1Stats.setPlayer(steelersRB1);
        steelersRB1Stats.setPassAttempts(0);
        steelersRB1Stats.setPassCompletions(0);
        steelersRB1Stats.setPassingYards(0);
        steelersRB1Stats.setPassingTouchdowns(0);
        steelersRB1Stats.setReceptions(40);
        steelersRB1Stats.setReceivingYards(367);
        steelersRB1Stats.setReceivingTouchdowns(0);
        steelersRB1Stats.setRushingAttempts(200);
        steelersRB1Stats.setRushingYards(907);
        steelersRB1Stats.setRushingTouchdowns(11);
        steelersRB1.getStats().add(steelersRB1Stats);

        //Bell
        Stats steelersRB2Stats = new Stats();
        steelersRB2Stats.setSeason(season2015);
        steelersRB2Stats.setPlayer(steelersRB1);
        steelersRB2Stats.setPassAttempts(0);
        steelersRB2Stats.setPassCompletions(0);
        steelersRB2Stats.setPassingYards(0);
        steelersRB2Stats.setPassingTouchdowns(0);
        steelersRB2Stats.setReceptions(0);
        steelersRB2Stats.setReceivingYards(0);
        steelersRB2Stats.setReceivingTouchdowns(0);
        steelersRB2Stats.setRushingAttempts(24);
        steelersRB2Stats.setRushingYards(136);
        steelersRB2Stats.setRushingTouchdowns(0);
        steelersRB2.getStats().add(steelersRB2Stats);

        //Brown
        Stats steelersWR1Stats = new Stats();
        steelersWR1Stats.setSeason(season2015);
        steelersWR1Stats.setPlayer(steelersWR1);
        steelersWR1Stats.setPassAttempts(0);
        steelersWR1Stats.setPassCompletions(0);
        steelersWR1Stats.setPassingYards(0);
        steelersWR1Stats.setPassingTouchdowns(0);
        steelersWR1Stats.setReceptions(136);
        steelersWR1Stats.setReceivingYards(1834);
        steelersWR1Stats.setReceivingTouchdowns(10);
        steelersWR1Stats.setRushingAttempts(3);
        steelersWR1Stats.setRushingYards(28);
        steelersWR1Stats.setRushingTouchdowns(0);
        steelersWR1.getStats().add(steelersWR1Stats);

        //Bryan
        Stats steelersWR2Stats = new Stats();
        steelersWR2Stats.setSeason(season2015);
        steelersWR2Stats.setPlayer(steelersWR1);
        steelersWR2Stats.setPassAttempts(0);
        steelersWR2Stats.setPassCompletions(0);
        steelersWR2Stats.setPassingYards(0);
        steelersWR2Stats.setPassingTouchdowns(0);
        steelersWR2Stats.setReceptions(50);
        steelersWR2Stats.setReceivingYards(764);
        steelersWR2Stats.setReceivingTouchdowns(6);
        steelersWR2Stats.setRushingAttempts(0);
        steelersWR2Stats.setRushingYards(0);
        steelersWR2Stats.setRushingTouchdowns(0);
        steelersWR2.getStats().add(steelersWR2Stats);

        //Bengals
        //Dalton
        Stats bengalsQBStats = new Stats();
        bengalsQBStats.setSeason(season2015);
        bengalsQBStats.setPlayer(bengalsQB);
        bengalsQBStats.setPassAttempts(386);
        bengalsQBStats.setPassCompletions(255);
        bengalsQBStats.setPassingYards(3250);
        bengalsQBStats.setPassingTouchdowns(25);
        bengalsQBStats.setReceptions(0);
        bengalsQBStats.setReceivingYards(0);
        bengalsQBStats.setReceivingTouchdowns(0);
        bengalsQBStats.setRushingAttempts(57);
        bengalsQBStats.setRushingYards(142);
        bengalsQBStats.setRushingTouchdowns(3);
        bengalsQB.getStats().add(bengalsQBStats);

        //Hill
        Stats bengalsRB1Stats = new Stats();
        bengalsRB1Stats.setSeason(season2015);
        bengalsRB1Stats.setPlayer(bengalsRB1);
        bengalsRB1Stats.setPassAttempts(0);
        bengalsRB1Stats.setPassCompletions(0);
        bengalsRB1Stats.setPassingYards(0);
        bengalsRB1Stats.setPassingTouchdowns(0);
        bengalsRB1Stats.setReceptions(15);
        bengalsRB1Stats.setReceivingYards(79);
        bengalsRB1Stats.setReceivingTouchdowns(1);
        bengalsRB1Stats.setRushingAttempts(223);
        bengalsRB1Stats.setRushingYards(794);
        bengalsRB1Stats.setRushingTouchdowns(11);
        bengalsRB1.getStats().add(bengalsRB1Stats);

        //Bernard
        Stats bengalsRB2Stats = new Stats();
        bengalsRB2Stats.setSeason(season2015);
        bengalsRB2Stats.setPlayer(bengalsRB1);
        bengalsRB2Stats.setPassAttempts(0);
        bengalsRB2Stats.setPassCompletions(0);
        bengalsRB2Stats.setPassingYards(0);
        bengalsRB2Stats.setPassingTouchdowns(0);
        bengalsRB2Stats.setReceptions(49);
        bengalsRB2Stats.setReceivingYards(472);
        bengalsRB2Stats.setReceivingTouchdowns(0);
        bengalsRB2Stats.setRushingAttempts(154);
        bengalsRB2Stats.setRushingYards(730);
        bengalsRB2Stats.setRushingTouchdowns(2);
        bengalsRB2.getStats().add(bengalsRB2Stats);

        //Green
        Stats bengalsWR1Stats = new Stats();
        bengalsWR1Stats.setSeason(season2015);
        bengalsWR1Stats.setPlayer(bengalsWR1);
        bengalsWR1Stats.setPassAttempts(0);
        bengalsWR1Stats.setPassCompletions(0);
        bengalsWR1Stats.setPassingYards(0);
        bengalsWR1Stats.setPassingTouchdowns(0);
        bengalsWR1Stats.setReceptions(86);
        bengalsWR1Stats.setReceivingYards(1297);
        bengalsWR1Stats.setReceivingTouchdowns(10);
        bengalsWR1Stats.setRushingAttempts(0);
        bengalsWR1Stats.setRushingYards(0);
        bengalsWR1Stats.setRushingTouchdowns(0);
        bengalsWR1.getStats().add(bengalsWR1Stats);

        //Jones
        Stats bengalsWR2Stats = new Stats();
        bengalsWR2Stats.setSeason(season2015);
        bengalsWR2Stats.setPlayer(bengalsWR1);
        bengalsWR2Stats.setPassAttempts(0);
        bengalsWR2Stats.setPassCompletions(0);
        bengalsWR2Stats.setPassingYards(0);
        bengalsWR2Stats.setPassingTouchdowns(0);
        bengalsWR2Stats.setReceptions(65);
        bengalsWR2Stats.setReceivingYards(816);
        bengalsWR2Stats.setReceivingTouchdowns(4);
        bengalsWR2Stats.setRushingAttempts(5);
        bengalsWR2Stats.setRushingYards(33);
        bengalsWR2Stats.setRushingTouchdowns(0);
        bengalsWR2.getStats().add(bengalsWR2Stats);

        //Browns
        //McCown
        Stats brownsQBStats = new Stats();
        brownsQBStats.setSeason(season2015);
        brownsQBStats.setPlayer(brownsQB);
        brownsQBStats.setPassAttempts(292);
        brownsQBStats.setPassCompletions(186);
        brownsQBStats.setPassingYards(2109);
        brownsQBStats.setPassingTouchdowns(12);
        brownsQBStats.setReceptions(0);
        brownsQBStats.setReceivingYards(0);
        brownsQBStats.setReceivingTouchdowns(0);
        brownsQBStats.setRushingAttempts(20);
        brownsQBStats.setRushingYards(98);
        brownsQBStats.setRushingTouchdowns(1);
        brownsQB.getStats().add(brownsQBStats);

        //Crowell
        Stats brownsRB1Stats = new Stats();
        brownsRB1Stats.setSeason(season2015);
        brownsRB1Stats.setPlayer(brownsRB1);
        brownsRB1Stats.setPassAttempts(0);
        brownsRB1Stats.setPassCompletions(0);
        brownsRB1Stats.setPassingYards(0);
        brownsRB1Stats.setPassingTouchdowns(0);
        brownsRB1Stats.setReceptions(19);
        brownsRB1Stats.setReceivingYards(182);
        brownsRB1Stats.setReceivingTouchdowns(1);
        brownsRB1Stats.setRushingAttempts(185);
        brownsRB1Stats.setRushingYards(706);
        brownsRB1Stats.setRushingTouchdowns(4);
        brownsRB1.getStats().add(brownsRB1Stats);

        //Johnson Jr.
        Stats brownsRB2Stats = new Stats();
        brownsRB2Stats.setSeason(season2015);
        brownsRB2Stats.setPlayer(brownsRB1);
        brownsRB2Stats.setPassAttempts(0);
        brownsRB2Stats.setPassCompletions(0);
        brownsRB2Stats.setPassingYards(0);
        brownsRB2Stats.setPassingTouchdowns(0);
        brownsRB2Stats.setReceptions(61);
        brownsRB2Stats.setReceivingYards(534);
        brownsRB2Stats.setReceivingTouchdowns(2);
        brownsRB2Stats.setRushingAttempts(104);
        brownsRB2Stats.setRushingYards(379);
        brownsRB2Stats.setRushingTouchdowns(2);
        brownsRB2.getStats().add(brownsRB2Stats);

        //Benjamin
        Stats brownsWR1Stats = new Stats();
        brownsWR1Stats.setSeason(season2015);
        brownsWR1Stats.setPlayer(brownsWR1);
        brownsWR1Stats.setPassAttempts(0);
        brownsWR1Stats.setPassCompletions(0);
        brownsWR1Stats.setPassingYards(0);
        brownsWR1Stats.setPassingTouchdowns(0);
        brownsWR1Stats.setReceptions(68);
        brownsWR1Stats.setReceivingYards(966);
        brownsWR1Stats.setReceivingTouchdowns(5);
        brownsWR1Stats.setRushingAttempts(4);
        brownsWR1Stats.setRushingYards(12);
        brownsWR1Stats.setRushingTouchdowns(0);
        brownsWR1.getStats().add(brownsWR1Stats);

        //Hartline
        Stats brownsWR2Stats = new Stats();
        brownsWR2Stats.setSeason(season2015);
        brownsWR2Stats.setPlayer(brownsWR1);
        brownsWR2Stats.setPassAttempts(0);
        brownsWR2Stats.setPassCompletions(0);
        brownsWR2Stats.setPassingYards(0);
        brownsWR2Stats.setPassingTouchdowns(0);
        brownsWR2Stats.setReceptions(46);
        brownsWR2Stats.setReceivingYards(523);
        brownsWR2Stats.setReceivingTouchdowns(2);
        brownsWR2Stats.setRushingAttempts(0);
        brownsWR2Stats.setRushingYards(0);
        brownsWR2Stats.setRushingTouchdowns(0);
        brownsWR2.getStats().add(brownsWR2Stats);

        //Jaguars
        //Bortles
        Stats jaguarsQBStats = new Stats();
        jaguarsQBStats.setSeason(season2015);
        jaguarsQBStats.setPlayer(jaguarsQB);
        jaguarsQBStats.setPassAttempts(606);
        jaguarsQBStats.setPassCompletions(355);
        jaguarsQBStats.setPassingYards(4428);
        jaguarsQBStats.setPassingTouchdowns(35);
        jaguarsQBStats.setReceptions(0);
        jaguarsQBStats.setReceivingYards(0);
        jaguarsQBStats.setReceivingTouchdowns(0);
        jaguarsQBStats.setRushingAttempts(52);
        jaguarsQBStats.setRushingYards(310);
        jaguarsQBStats.setRushingTouchdowns(2);
        jaguarsQB.getStats().add(jaguarsQBStats);

        //Yeldon
        Stats jaguarsRB1Stats = new Stats();
        jaguarsRB1Stats.setSeason(season2015);
        jaguarsRB1Stats.setPlayer(jaguarsRB1);
        jaguarsRB1Stats.setPassAttempts(0);
        jaguarsRB1Stats.setPassCompletions(0);
        jaguarsRB1Stats.setPassingYards(0);
        jaguarsRB1Stats.setPassingTouchdowns(0);
        jaguarsRB1Stats.setReceptions(36);
        jaguarsRB1Stats.setReceivingYards(279);
        jaguarsRB1Stats.setReceivingTouchdowns(1);
        jaguarsRB1Stats.setRushingAttempts(182);
        jaguarsRB1Stats.setRushingYards(740);
        jaguarsRB1Stats.setRushingTouchdowns(2);
        jaguarsRB1.getStats().add(jaguarsRB1Stats);

        //Robinson
        Stats jaguarsRB2Stats = new Stats();
        jaguarsRB2Stats.setSeason(season2015);
        jaguarsRB2Stats.setPlayer(jaguarsRB1);
        jaguarsRB2Stats.setPassAttempts(0);
        jaguarsRB2Stats.setPassCompletions(0);
        jaguarsRB2Stats.setPassingYards(0);
        jaguarsRB2Stats.setPassingTouchdowns(0);
        jaguarsRB2Stats.setReceptions(21);
        jaguarsRB2Stats.setReceivingYards(164);
        jaguarsRB2Stats.setReceivingTouchdowns(0);
        jaguarsRB2Stats.setRushingAttempts(67);
        jaguarsRB2Stats.setRushingYards(266);
        jaguarsRB2Stats.setRushingTouchdowns(1);
        jaguarsRB2.getStats().add(jaguarsRB2Stats);

        //Robinson
        Stats jaguarsWR1Stats = new Stats();
        jaguarsWR1Stats.setSeason(season2015);
        jaguarsWR1Stats.setPlayer(jaguarsWR1);
        jaguarsWR1Stats.setPassAttempts(0);
        jaguarsWR1Stats.setPassCompletions(0);
        jaguarsWR1Stats.setPassingYards(0);
        jaguarsWR1Stats.setPassingTouchdowns(0);
        jaguarsWR1Stats.setReceptions(80);
        jaguarsWR1Stats.setReceivingYards(1400);
        jaguarsWR1Stats.setReceivingTouchdowns(14);
        jaguarsWR1Stats.setRushingAttempts(0);
        jaguarsWR1Stats.setRushingYards(0);
        jaguarsWR1Stats.setRushingTouchdowns(0);
        jaguarsWR1.getStats().add(jaguarsWR1Stats);

        //Herns
        Stats jaguarsWR2Stats = new Stats();
        jaguarsWR2Stats.setSeason(season2015);
        jaguarsWR2Stats.setPlayer(jaguarsWR1);
        jaguarsWR2Stats.setPassAttempts(0);
        jaguarsWR2Stats.setPassCompletions(0);
        jaguarsWR2Stats.setPassingYards(0);
        jaguarsWR2Stats.setPassingTouchdowns(0);
        jaguarsWR2Stats.setReceptions(64);
        jaguarsWR2Stats.setReceivingYards(1031);
        jaguarsWR2Stats.setReceivingTouchdowns(10);
        jaguarsWR2Stats.setRushingAttempts(0);
        jaguarsWR2Stats.setRushingYards(0);
        jaguarsWR2Stats.setRushingTouchdowns(0);
        jaguarsWR2.getStats().add(jaguarsWR2Stats);

        //Titans
        //Mariota
        Stats titansQBStats = new Stats();
        titansQBStats.setSeason(season2015);
        titansQBStats.setPlayer(titansQB);
        titansQBStats.setPassAttempts(370);
        titansQBStats.setPassCompletions(230);
        titansQBStats.setPassingYards(2818);
        titansQBStats.setPassingTouchdowns(19);
        titansQBStats.setReceptions(0);
        titansQBStats.setReceivingYards(0);
        titansQBStats.setReceivingTouchdowns(0);
        titansQBStats.setRushingAttempts(34);
        titansQBStats.setRushingYards(252);
        titansQBStats.setRushingTouchdowns(2);
        titansQB.getStats().add(titansQBStats);

        //Andrews
        Stats titansRB1Stats = new Stats();
        titansRB1Stats.setSeason(season2015);
        titansRB1Stats.setPlayer(titansRB1);
        titansRB1Stats.setPassAttempts(0);
        titansRB1Stats.setPassCompletions(0);
        titansRB1Stats.setPassingYards(0);
        titansRB1Stats.setPassingTouchdowns(0);
        titansRB1Stats.setReceptions(21);
        titansRB1Stats.setReceivingYards(174);
        titansRB1Stats.setReceivingTouchdowns(0);
        titansRB1Stats.setRushingAttempts(143);
        titansRB1Stats.setRushingYards(520);
        titansRB1Stats.setRushingTouchdowns(3);
        titansRB1.getStats().add(titansRB1Stats);

        //McCluster
        Stats titansRB2Stats = new Stats();
        titansRB2Stats.setSeason(season2015);
        titansRB2Stats.setPlayer(titansRB1);
        titansRB2Stats.setPassAttempts(0);
        titansRB2Stats.setPassCompletions(0);
        titansRB2Stats.setPassingYards(0);
        titansRB2Stats.setPassingTouchdowns(0);
        titansRB2Stats.setReceptions(31);
        titansRB2Stats.setReceivingYards(260);
        titansRB2Stats.setReceivingTouchdowns(1);
        titansRB2Stats.setRushingAttempts(55);
        titansRB2Stats.setRushingYards(247);
        titansRB2Stats.setRushingTouchdowns(1);
        titansRB2.getStats().add(titansRB2Stats);

        //Green-Beckham
        Stats titansWR1Stats = new Stats();
        titansWR1Stats.setSeason(season2015);
        titansWR1Stats.setPlayer(titansWR1);
        titansWR1Stats.setPassAttempts(0);
        titansWR1Stats.setPassCompletions(0);
        titansWR1Stats.setPassingYards(0);
        titansWR1Stats.setPassingTouchdowns(0);
        titansWR1Stats.setReceptions(32);
        titansWR1Stats.setReceivingYards(549);
        titansWR1Stats.setReceivingTouchdowns(4);
        titansWR1Stats.setRushingAttempts(0);
        titansWR1Stats.setRushingYards(0);
        titansWR1Stats.setRushingTouchdowns(0);
        titansWR1.getStats().add(titansWR1Stats);

        //Douglas
        Stats titansWR2Stats = new Stats();
        titansWR2Stats.setSeason(season2015);
        titansWR2Stats.setPlayer(titansWR1);
        titansWR2Stats.setPassAttempts(0);
        titansWR2Stats.setPassCompletions(0);
        titansWR2Stats.setPassingYards(0);
        titansWR2Stats.setPassingTouchdowns(0);
        titansWR2Stats.setReceptions(36);
        titansWR2Stats.setReceivingYards(411);
        titansWR2Stats.setReceivingTouchdowns(2);
        titansWR2Stats.setRushingAttempts(0);
        titansWR2Stats.setRushingYards(0);
        titansWR2Stats.setRushingTouchdowns(0);
        titansWR2.getStats().add(titansWR2Stats);

        //Seahawks
        //Wilson
        Stats seahawksQBStats = new Stats();
        seahawksQBStats.setSeason(season2015);
        seahawksQBStats.setPlayer(seahawksQB);
        seahawksQBStats.setPassAttempts(483);
        seahawksQBStats.setPassCompletions(329);
        seahawksQBStats.setPassingYards(4024);
        seahawksQBStats.setPassingTouchdowns(34);
        seahawksQBStats.setReceptions(0);
        seahawksQBStats.setReceivingYards(0);
        seahawksQBStats.setReceivingTouchdowns(0);
        seahawksQBStats.setRushingAttempts(103);
        seahawksQBStats.setRushingYards(553);
        seahawksQBStats.setRushingTouchdowns(1);
        seahawksQB.getStats().add(seahawksQBStats);

        //Rawls
        Stats seahawksRB1Stats = new Stats();
        seahawksRB1Stats.setSeason(season2015);
        seahawksRB1Stats.setPlayer(seahawksRB1);
        seahawksRB1Stats.setPassAttempts(0);
        seahawksRB1Stats.setPassCompletions(0);
        seahawksRB1Stats.setPassingYards(0);
        seahawksRB1Stats.setPassingTouchdowns(0);
        seahawksRB1Stats.setReceptions(9);
        seahawksRB1Stats.setReceivingYards(11);
        seahawksRB1Stats.setReceivingTouchdowns(1);
        seahawksRB1Stats.setRushingAttempts(147);
        seahawksRB1Stats.setRushingYards(830);
        seahawksRB1Stats.setRushingTouchdowns(4);
        seahawksRB1.getStats().add(seahawksRB1Stats);

        //Lynch
        Stats seahawksRB2Stats = new Stats();
        seahawksRB2Stats.setSeason(season2015);
        seahawksRB2Stats.setPlayer(seahawksRB1);
        seahawksRB2Stats.setPassAttempts(0);
        seahawksRB2Stats.setPassCompletions(0);
        seahawksRB2Stats.setPassingYards(0);
        seahawksRB2Stats.setPassingTouchdowns(0);
        seahawksRB2Stats.setReceptions(13);
        seahawksRB2Stats.setReceivingYards(80);
        seahawksRB2Stats.setReceivingTouchdowns(0);
        seahawksRB2Stats.setRushingAttempts(111);
        seahawksRB2Stats.setRushingYards(417);
        seahawksRB2Stats.setRushingTouchdowns(3);
        seahawksRB2.getStats().add(seahawksRB2Stats);

        //Baldwin
        Stats seahawksWR1Stats = new Stats();
        seahawksWR1Stats.setSeason(season2015);
        seahawksWR1Stats.setPlayer(seahawksWR1);
        seahawksWR1Stats.setPassAttempts(0);
        seahawksWR1Stats.setPassCompletions(0);
        seahawksWR1Stats.setPassingYards(0);
        seahawksWR1Stats.setPassingTouchdowns(0);
        seahawksWR1Stats.setReceptions(78);
        seahawksWR1Stats.setReceivingYards(1069);
        seahawksWR1Stats.setReceivingTouchdowns(14);
        seahawksWR1Stats.setRushingAttempts(0);
        seahawksWR1Stats.setRushingYards(0);
        seahawksWR1Stats.setRushingTouchdowns(0);
        seahawksWR1.getStats().add(seahawksWR1Stats);

        //Kearse
        Stats seahawksWR2Stats = new Stats();
        seahawksWR2Stats.setSeason(season2015);
        seahawksWR2Stats.setPlayer(seahawksWR1);
        seahawksWR2Stats.setPassAttempts(0);
        seahawksWR2Stats.setPassCompletions(0);
        seahawksWR2Stats.setPassingYards(0);
        seahawksWR2Stats.setPassingTouchdowns(0);
        seahawksWR2Stats.setReceptions(49);
        seahawksWR2Stats.setReceivingYards(685);
        seahawksWR2Stats.setReceivingTouchdowns(5);
        seahawksWR2Stats.setRushingAttempts(0);
        seahawksWR2Stats.setRushingYards(0);
        seahawksWR2Stats.setRushingTouchdowns(0);
        seahawksWR2.getStats().add(seahawksWR2Stats);

        //Rams
        //Foles
        Stats ramsQBStats = new Stats();
        ramsQBStats.setSeason(season2015);
        ramsQBStats.setPlayer(ramsQB);
        ramsQBStats.setPassAttempts(337);
        ramsQBStats.setPassCompletions(190);
        ramsQBStats.setPassingYards(2052);
        ramsQBStats.setPassingTouchdowns(7);
        ramsQBStats.setReceptions(0);
        ramsQBStats.setReceivingYards(0);
        ramsQBStats.setReceivingTouchdowns(0);
        ramsQBStats.setRushingAttempts(17);
        ramsQBStats.setRushingYards(20);
        ramsQBStats.setRushingTouchdowns(1);
        ramsQB.getStats().add(ramsQBStats);

        //Gurley
        Stats ramsRB1Stats = new Stats();
        ramsRB1Stats.setSeason(season2015);
        ramsRB1Stats.setPlayer(ramsRB1);
        ramsRB1Stats.setPassAttempts(0);
        ramsRB1Stats.setPassCompletions(0);
        ramsRB1Stats.setPassingYards(0);
        ramsRB1Stats.setPassingTouchdowns(0);
        ramsRB1Stats.setReceptions(21);
        ramsRB1Stats.setReceivingYards(188);
        ramsRB1Stats.setReceivingTouchdowns(0);
        ramsRB1Stats.setRushingAttempts(229);
        ramsRB1Stats.setRushingYards(1106);
        ramsRB1Stats.setRushingTouchdowns(10);
        ramsRB1.getStats().add(ramsRB1Stats);

        //Austin
        Stats ramsRB2Stats = new Stats();
        ramsRB2Stats.setSeason(season2015);
        ramsRB2Stats.setPlayer(ramsRB1);
        ramsRB2Stats.setPassAttempts(0);
        ramsRB2Stats.setPassCompletions(0);
        ramsRB2Stats.setPassingYards(0);
        ramsRB2Stats.setPassingTouchdowns(0);
        ramsRB2Stats.setReceptions(52);
        ramsRB2Stats.setReceivingYards(473);
        ramsRB2Stats.setReceivingTouchdowns(5);
        ramsRB2Stats.setRushingAttempts(52);
        ramsRB2Stats.setRushingYards(434);
        ramsRB2Stats.setRushingTouchdowns(4);
        ramsRB2.getStats().add(ramsRB2Stats);

        //Britt
        Stats ramsWR1Stats = new Stats();
        ramsWR1Stats.setSeason(season2015);
        ramsWR1Stats.setPlayer(ramsWR1);
        ramsWR1Stats.setPassAttempts(0);
        ramsWR1Stats.setPassCompletions(0);
        ramsWR1Stats.setPassingYards(0);
        ramsWR1Stats.setPassingTouchdowns(0);
        ramsWR1Stats.setReceptions(36);
        ramsWR1Stats.setReceivingYards(681);
        ramsWR1Stats.setReceivingTouchdowns(3);
        ramsWR1Stats.setRushingAttempts(0);
        ramsWR1Stats.setRushingYards(0);
        ramsWR1Stats.setRushingTouchdowns(0);
        ramsWR1.getStats().add(ramsWR1Stats);

        //Cunningham
        Stats ramsWR2Stats = new Stats();
        ramsWR2Stats.setSeason(season2015);
        ramsWR2Stats.setPlayer(ramsWR1);
        ramsWR2Stats.setPassAttempts(0);
        ramsWR2Stats.setPassCompletions(0);
        ramsWR2Stats.setPassingYards(0);
        ramsWR2Stats.setPassingTouchdowns(0);
        ramsWR2Stats.setReceptions(26);
        ramsWR2Stats.setReceivingYards(250);
        ramsWR2Stats.setReceivingTouchdowns(0);
        ramsWR2Stats.setRushingAttempts(0);
        ramsWR2Stats.setRushingYards(0);
        ramsWR2Stats.setRushingTouchdowns(0);
        ramsWR2.getStats().add(ramsWR2Stats);

        //49ers
        //Gabbert
        Stats ninersQBStats = new Stats();
        ninersQBStats.setSeason(season2015);
        ninersQBStats.setPlayer(ninersQB);
        ninersQBStats.setPassAttempts(282);
        ninersQBStats.setPassCompletions(178);
        ninersQBStats.setPassingYards(2031);
        ninersQBStats.setPassingTouchdowns(10);
        ninersQBStats.setReceptions(0);
        ninersQBStats.setReceivingYards(0);
        ninersQBStats.setReceivingTouchdowns(0);
        ninersQBStats.setRushingAttempts(32);
        ninersQBStats.setRushingYards(185);
        ninersQBStats.setRushingTouchdowns(1);
        ninersQB.getStats().add(ninersQBStats);

        //Hyde
        Stats ninersRB1Stats = new Stats();
        ninersRB1Stats.setSeason(season2015);
        ninersRB1Stats.setPlayer(ninersRB1);
        ninersRB1Stats.setPassAttempts(0);
        ninersRB1Stats.setPassCompletions(0);
        ninersRB1Stats.setPassingYards(0);
        ninersRB1Stats.setPassingTouchdowns(0);
        ninersRB1Stats.setReceptions(11);
        ninersRB1Stats.setReceivingYards(15);
        ninersRB1Stats.setReceivingTouchdowns(0);
        ninersRB1Stats.setRushingAttempts(115);
        ninersRB1Stats.setRushingYards(470);
        ninersRB1Stats.setRushingTouchdowns(3);
        ninersRB1.getStats().add(ninersRB1Stats);

        //Draughn
        Stats ninersRB2Stats = new Stats();
        ninersRB2Stats.setSeason(season2015);
        ninersRB2Stats.setPlayer(ninersRB1);
        ninersRB2Stats.setPassAttempts(0);
        ninersRB2Stats.setPassCompletions(0);
        ninersRB2Stats.setPassingYards(0);
        ninersRB2Stats.setPassingTouchdowns(0);
        ninersRB2Stats.setReceptions(25);
        ninersRB2Stats.setReceivingYards(32);
        ninersRB2Stats.setReceivingTouchdowns(0);
        ninersRB2Stats.setRushingAttempts(76);
        ninersRB2Stats.setRushingYards(263);
        ninersRB2Stats.setRushingTouchdowns(1);
        ninersRB2.getStats().add(ninersRB2Stats);

        //Boldin
        Stats ninersWR1Stats = new Stats();
        ninersWR1Stats.setSeason(season2015);
        ninersWR1Stats.setPlayer(ninersWR1);
        ninersWR1Stats.setPassAttempts(0);
        ninersWR1Stats.setPassCompletions(0);
        ninersWR1Stats.setPassingYards(0);
        ninersWR1Stats.setPassingTouchdowns(0);
        ninersWR1Stats.setReceptions(69);
        ninersWR1Stats.setReceivingYards(789);
        ninersWR1Stats.setReceivingTouchdowns(4);
        ninersWR1Stats.setRushingAttempts(0);
        ninersWR1Stats.setRushingYards(0);
        ninersWR1Stats.setRushingTouchdowns(0);
        ninersWR1.getStats().add(ninersWR1Stats);

        //Smith
        Stats ninersWR2Stats = new Stats();
        ninersWR2Stats.setSeason(season2015);
        ninersWR2Stats.setPlayer(ninersWR1);
        ninersWR2Stats.setPassAttempts(0);
        ninersWR2Stats.setPassCompletions(0);
        ninersWR2Stats.setPassingYards(0);
        ninersWR2Stats.setPassingTouchdowns(0);
        ninersWR2Stats.setReceptions(33);
        ninersWR2Stats.setReceivingYards(663);
        ninersWR2Stats.setReceivingTouchdowns(4);
        ninersWR2Stats.setRushingAttempts(0);
        ninersWR2Stats.setRushingYards(0);
        ninersWR2Stats.setRushingTouchdowns(0);
        ninersWR2.getStats().add(ninersWR2Stats);

        //Cardinals
        //Palmer
        Stats cardinalsQBStats = new Stats();
        cardinalsQBStats.setSeason(season2015);
        cardinalsQBStats.setPlayer(cardinalsQB);
        cardinalsQBStats.setPassAttempts(537);
        cardinalsQBStats.setPassCompletions(342);
        cardinalsQBStats.setPassingYards(2031);
        cardinalsQBStats.setPassingTouchdowns(35);
        cardinalsQBStats.setReceptions(0);
        cardinalsQBStats.setReceivingYards(0);
        cardinalsQBStats.setReceivingTouchdowns(0);
        cardinalsQBStats.setRushingAttempts(25);
        cardinalsQBStats.setRushingYards(14);
        cardinalsQBStats.setRushingTouchdowns(1);
        cardinalsQB.getStats().add(cardinalsQBStats);

        //Chris Johnson
        Stats cardinalsRB1Stats = new Stats();
        cardinalsRB1Stats.setSeason(season2015);
        cardinalsRB1Stats.setPlayer(cardinalsRB1);
        cardinalsRB1Stats.setPassAttempts(0);
        cardinalsRB1Stats.setPassCompletions(0);
        cardinalsRB1Stats.setPassingYards(0);
        cardinalsRB1Stats.setPassingTouchdowns(0);
        cardinalsRB1Stats.setReceptions(6);
        cardinalsRB1Stats.setReceivingYards(58);
        cardinalsRB1Stats.setReceivingTouchdowns(0);
        cardinalsRB1Stats.setRushingAttempts(196);
        cardinalsRB1Stats.setRushingYards(814);
        cardinalsRB1Stats.setRushingTouchdowns(3);
        cardinalsRB1.getStats().add(cardinalsRB1Stats);

        //David Johnson
        Stats cardinalsRB2Stats = new Stats();
        cardinalsRB2Stats.setSeason(season2015);
        cardinalsRB2Stats.setPlayer(cardinalsRB1);
        cardinalsRB2Stats.setPassAttempts(0);
        cardinalsRB2Stats.setPassCompletions(0);
        cardinalsRB2Stats.setPassingYards(0);
        cardinalsRB2Stats.setPassingTouchdowns(0);
        cardinalsRB2Stats.setReceptions(36);
        cardinalsRB2Stats.setReceivingYards(457);
        cardinalsRB2Stats.setReceivingTouchdowns(4);
        cardinalsRB2Stats.setRushingAttempts(125);
        cardinalsRB2Stats.setRushingYards(581);
        cardinalsRB2Stats.setRushingTouchdowns(8);
        cardinalsRB2.getStats().add(cardinalsRB2Stats);

        //Fitzgerald
        Stats cardinalsWR1Stats = new Stats();
        cardinalsWR1Stats.setSeason(season2015);
        cardinalsWR1Stats.setPlayer(cardinalsRB1);
        cardinalsWR1Stats.setPassAttempts(0);
        cardinalsWR1Stats.setPassCompletions(0);
        cardinalsWR1Stats.setPassingYards(0);
        cardinalsWR1Stats.setPassingTouchdowns(0);
        cardinalsWR1Stats.setReceptions(109);
        cardinalsWR1Stats.setReceivingYards(1215);
        cardinalsWR1Stats.setReceivingTouchdowns(9);
        cardinalsWR1Stats.setRushingAttempts(0);
        cardinalsWR1Stats.setRushingYards(0);
        cardinalsWR1Stats.setRushingTouchdowns(0);
        cardinalsWR1.getStats().add(cardinalsWR1Stats);

        //Brown
        Stats cardinalsWR2Stats = new Stats();
        cardinalsWR2Stats.setSeason(season2015);
        cardinalsWR2Stats.setPlayer(cardinalsWR1);
        cardinalsWR2Stats.setPassAttempts(0);
        cardinalsWR2Stats.setPassCompletions(0);
        cardinalsWR2Stats.setPassingYards(0);
        cardinalsWR2Stats.setPassingTouchdowns(0);
        cardinalsWR2Stats.setReceptions(65);
        cardinalsWR2Stats.setReceivingYards(1003);
        cardinalsWR2Stats.setReceivingTouchdowns(7);
        cardinalsWR2Stats.setRushingAttempts(0);
        cardinalsWR2Stats.setRushingYards(0);
        cardinalsWR2Stats.setRushingTouchdowns(0);
        cardinalsWR2.getStats().add(cardinalsWR2Stats);

        //Panthers
        //Newton
        Stats panthersQBStats = new Stats();
        panthersQBStats.setSeason(season2015);
        panthersQBStats.setPlayer(panthersQB);
        panthersQBStats.setPassAttempts(495);
        panthersQBStats.setPassCompletions(296);
        panthersQBStats.setPassingYards(3837);
        panthersQBStats.setPassingTouchdowns(35);
        panthersQBStats.setReceptions(0);
        panthersQBStats.setReceivingYards(0);
        panthersQBStats.setReceivingTouchdowns(0);
        panthersQBStats.setRushingAttempts(132);
        panthersQBStats.setRushingYards(636);
        panthersQBStats.setRushingTouchdowns(10);
        panthersQB.getStats().add(panthersQBStats);

        //Stewart
        Stats panthersRB1Stats = new Stats();
        panthersRB1Stats.setSeason(season2015);
        panthersRB1Stats.setPlayer(panthersRB1);
        panthersRB1Stats.setPassAttempts(0);
        panthersRB1Stats.setPassCompletions(0);
        panthersRB1Stats.setPassingYards(0);
        panthersRB1Stats.setPassingTouchdowns(0);
        panthersRB1Stats.setReceptions(16);
        panthersRB1Stats.setReceivingYards(99);
        panthersRB1Stats.setReceivingTouchdowns(1);
        panthersRB1Stats.setRushingAttempts(242);
        panthersRB1Stats.setRushingYards(989);
        panthersRB1Stats.setRushingTouchdowns(6);
        panthersRB1.getStats().add(panthersRB1Stats);

        //Tolbert
        Stats panthersRB2Stats = new Stats();
        panthersRB2Stats.setSeason(season2015);
        panthersRB2Stats.setPlayer(panthersRB1);
        panthersRB2Stats.setPassAttempts(0);
        panthersRB2Stats.setPassCompletions(0);
        panthersRB2Stats.setPassingYards(0);
        panthersRB2Stats.setPassingTouchdowns(0);
        panthersRB2Stats.setReceptions(18);
        panthersRB2Stats.setReceivingYards(154);
        panthersRB2Stats.setReceivingTouchdowns(3);
        panthersRB2Stats.setRushingAttempts(62);
        panthersRB2Stats.setRushingYards(256);
        panthersRB2Stats.setRushingTouchdowns(1);
        panthersRB2.getStats().add(panthersRB2Stats);

        //Ginn Jr.
        Stats panthersWR1Stats = new Stats();
        panthersWR1Stats.setSeason(season2015);
        panthersWR1Stats.setPlayer(panthersRB1);
        panthersWR1Stats.setPassAttempts(0);
        panthersWR1Stats.setPassCompletions(0);
        panthersWR1Stats.setPassingYards(0);
        panthersWR1Stats.setPassingTouchdowns(0);
        panthersWR1Stats.setReceptions(44);
        panthersWR1Stats.setReceivingYards(739);
        panthersWR1Stats.setReceivingTouchdowns(10);
        panthersWR1Stats.setRushingAttempts(4);
        panthersWR1Stats.setRushingYards(60);
        panthersWR1Stats.setRushingTouchdowns(0);
        panthersWR1.getStats().add(panthersWR1Stats);

        //Cotchery
        Stats panthersWR2Stats = new Stats();
        panthersWR2Stats.setSeason(season2015);
        panthersWR2Stats.setPlayer(panthersWR1);
        panthersWR2Stats.setPassAttempts(0);
        panthersWR2Stats.setPassCompletions(0);
        panthersWR2Stats.setPassingYards(0);
        panthersWR2Stats.setPassingTouchdowns(0);
        panthersWR2Stats.setReceptions(39);
        panthersWR2Stats.setReceivingYards(485);
        panthersWR2Stats.setReceivingTouchdowns(3);
        panthersWR2Stats.setRushingAttempts(1);
        panthersWR2Stats.setRushingYards(0);
        panthersWR2Stats.setRushingTouchdowns(0);
        panthersWR2.getStats().add(panthersWR2Stats);

        //Falcons
        //Ryan
        Stats falconsQBStats = new Stats();
        falconsQBStats.setSeason(season2015);
        falconsQBStats.setPlayer(falconsQB);
        falconsQBStats.setPassAttempts(614);
        falconsQBStats.setPassCompletions(407);
        falconsQBStats.setPassingYards(4591);
        falconsQBStats.setPassingTouchdowns(21);
        falconsQBStats.setReceptions(0);
        falconsQBStats.setReceivingYards(0);
        falconsQBStats.setReceivingTouchdowns(0);
        falconsQBStats.setRushingAttempts(37);
        falconsQBStats.setRushingYards(63);
        falconsQBStats.setRushingTouchdowns(0);
        falconsQB.getStats().add(falconsQBStats);

        //Freeman
        Stats falconsRB1Stats = new Stats();
        falconsRB1Stats.setSeason(season2015);
        falconsRB1Stats.setPlayer(falconsRB1);
        falconsRB1Stats.setPassAttempts(0);
        falconsRB1Stats.setPassCompletions(0);
        falconsRB1Stats.setPassingYards(0);
        falconsRB1Stats.setPassingTouchdowns(0);
        falconsRB1Stats.setReceptions(73);
        falconsRB1Stats.setReceivingYards(578);
        falconsRB1Stats.setReceivingTouchdowns(3);
        falconsRB1Stats.setRushingAttempts(264);
        falconsRB1Stats.setRushingYards(1061);
        falconsRB1Stats.setRushingTouchdowns(11);
        falconsRB1.getStats().add(falconsRB1Stats);

        //Colman
        Stats falconsRB2Stats = new Stats();
        falconsRB2Stats.setSeason(season2015);
        falconsRB2Stats.setPlayer(falconsRB1);
        falconsRB2Stats.setPassAttempts(0);
        falconsRB2Stats.setPassCompletions(0);
        falconsRB2Stats.setPassingYards(0);
        falconsRB2Stats.setPassingTouchdowns(0);
        falconsRB2Stats.setReceptions(18);
        falconsRB2Stats.setReceivingYards(154);
        falconsRB2Stats.setReceivingTouchdowns(3);
        falconsRB2Stats.setRushingAttempts(2);
        falconsRB2Stats.setRushingYards(14);
        falconsRB2Stats.setRushingTouchdowns(0);
        falconsRB2.getStats().add(falconsRB2Stats);

        //Jones
        Stats falconsWR1Stats = new Stats();
        falconsWR1Stats.setSeason(season2015);
        falconsWR1Stats.setPlayer(falconsRB1);
        falconsWR1Stats.setPassAttempts(0);
        falconsWR1Stats.setPassCompletions(0);
        falconsWR1Stats.setPassingYards(0);
        falconsWR1Stats.setPassingTouchdowns(0);
        falconsWR1Stats.setReceptions(136);
        falconsWR1Stats.setReceivingYards(1871);
        falconsWR1Stats.setReceivingTouchdowns(8);
        falconsWR1Stats.setRushingAttempts(0);
        falconsWR1Stats.setRushingYards(0);
        falconsWR1Stats.setRushingTouchdowns(0);
        falconsWR1.getStats().add(falconsWR1Stats);

        //White
        Stats falconsWR2Stats = new Stats();
        falconsWR2Stats.setSeason(season2015);
        falconsWR2Stats.setPlayer(falconsWR1);
        falconsWR2Stats.setPassAttempts(0);
        falconsWR2Stats.setPassCompletions(0);
        falconsWR2Stats.setPassingYards(0);
        falconsWR2Stats.setPassingTouchdowns(0);
        falconsWR2Stats.setReceptions(43);
        falconsWR2Stats.setReceivingYards(506);
        falconsWR2Stats.setReceivingTouchdowns(1);
        falconsWR2Stats.setRushingAttempts(0);
        falconsWR2Stats.setRushingYards(0);
        falconsWR2Stats.setRushingTouchdowns(0);
        falconsWR2.getStats().add(falconsWR2Stats);

        //Bucks
        //Winston
        Stats buccaneersQBStats = new Stats();
        buccaneersQBStats.setSeason(season2015);
        buccaneersQBStats.setPlayer(buccaneersQB);
        buccaneersQBStats.setPassAttempts(535);
        buccaneersQBStats.setPassCompletions(312);
        buccaneersQBStats.setPassingYards(4042);
        buccaneersQBStats.setPassingTouchdowns(22);
        buccaneersQBStats.setReceptions(0);
        buccaneersQBStats.setReceivingYards(0);
        buccaneersQBStats.setReceivingTouchdowns(6);
        buccaneersQBStats.setRushingAttempts(54);
        buccaneersQBStats.setRushingYards(213);
        buccaneersQBStats.setRushingTouchdowns(1);
        buccaneersQB.getStats().add(buccaneersQBStats);

        //Martin
        Stats buccaneersRB1Stats = new Stats();
        buccaneersRB1Stats.setSeason(season2015);
        buccaneersRB1Stats.setPlayer(buccaneersRB1);
        buccaneersRB1Stats.setPassAttempts(0);
        buccaneersRB1Stats.setPassCompletions(0);
        buccaneersRB1Stats.setPassingYards(0);
        buccaneersRB1Stats.setPassingTouchdowns(0);
        buccaneersRB1Stats.setReceptions(33);
        buccaneersRB1Stats.setReceivingYards(271);
        buccaneersRB1Stats.setReceivingTouchdowns(1);
        buccaneersRB1Stats.setRushingAttempts(288);
        buccaneersRB1Stats.setRushingYards(1402);
        buccaneersRB1Stats.setRushingTouchdowns(6);
        buccaneersRB1.getStats().add(buccaneersRB1Stats);

        //Sims
        Stats buccaneersRB2Stats = new Stats();
        buccaneersRB2Stats.setSeason(season2015);
        buccaneersRB2Stats.setPlayer(buccaneersRB1);
        buccaneersRB2Stats.setPassAttempts(0);
        buccaneersRB2Stats.setPassCompletions(0);
        buccaneersRB2Stats.setPassingYards(0);
        buccaneersRB2Stats.setPassingTouchdowns(0);
        buccaneersRB2Stats.setReceptions(51);
        buccaneersRB2Stats.setReceivingYards(561);
        buccaneersRB2Stats.setReceivingTouchdowns(4);
        buccaneersRB2Stats.setRushingAttempts(107);
        buccaneersRB2Stats.setRushingYards(529);
        buccaneersRB2Stats.setRushingTouchdowns(4);
        buccaneersRB2.getStats().add(buccaneersRB2Stats);

        //Evans
        Stats buccaneersWR1Stats = new Stats();
        buccaneersWR1Stats.setSeason(season2015);
        buccaneersWR1Stats.setPlayer(buccaneersRB1);
        buccaneersWR1Stats.setPassAttempts(0);
        buccaneersWR1Stats.setPassCompletions(0);
        buccaneersWR1Stats.setPassingYards(0);
        buccaneersWR1Stats.setPassingTouchdowns(0);
        buccaneersWR1Stats.setReceptions(74);
        buccaneersWR1Stats.setReceivingYards(1206);
        buccaneersWR1Stats.setReceivingTouchdowns(3);
        buccaneersWR1Stats.setRushingAttempts(0);
        buccaneersWR1Stats.setRushingYards(0);
        buccaneersWR1Stats.setRushingTouchdowns(0);
        buccaneersWR1.getStats().add(buccaneersWR1Stats);

        //Jackson
        Stats buccaneersWR2Stats = new Stats();
        buccaneersWR2Stats.setSeason(season2015);
        buccaneersWR2Stats.setPlayer(buccaneersWR1);
        buccaneersWR2Stats.setPassAttempts(0);
        buccaneersWR2Stats.setPassCompletions(0);
        buccaneersWR2Stats.setPassingYards(0);
        buccaneersWR2Stats.setPassingTouchdowns(0);
        buccaneersWR2Stats.setReceptions(33);
        buccaneersWR2Stats.setReceivingYards(543);
        buccaneersWR2Stats.setReceivingTouchdowns(3);
        buccaneersWR2Stats.setRushingAttempts(0);
        buccaneersWR2Stats.setRushingYards(0);
        buccaneersWR2Stats.setRushingTouchdowns(0);
        buccaneersWR2.getStats().add(buccaneersWR2Stats);

        //Saints
        //Brees
        Stats saintsQBStats = new Stats();
        saintsQBStats.setSeason(season2015);
        saintsQBStats.setPlayer(saintsQB);
        saintsQBStats.setPassAttempts(627);
        saintsQBStats.setPassCompletions(428);
        saintsQBStats.setPassingYards(4870);
        saintsQBStats.setPassingTouchdowns(32);
        saintsQBStats.setReceptions(0);
        saintsQBStats.setReceivingYards(0);
        saintsQBStats.setReceivingTouchdowns(0);
        saintsQBStats.setRushingAttempts(24);
        saintsQBStats.setRushingYards(14);
        saintsQBStats.setRushingTouchdowns(1);
        saintsQB.getStats().add(saintsQBStats);

        //Ingram
        Stats saintsRB1Stats = new Stats();
        saintsRB1Stats.setSeason(season2015);
        saintsRB1Stats.setPlayer(saintsRB1);
        saintsRB1Stats.setPassAttempts(0);
        saintsRB1Stats.setPassCompletions(0);
        saintsRB1Stats.setPassingYards(0);
        saintsRB1Stats.setPassingTouchdowns(0);
        saintsRB1Stats.setReceptions(50);
        saintsRB1Stats.setReceivingYards(405);
        saintsRB1Stats.setReceivingTouchdowns(3);
        saintsRB1Stats.setRushingAttempts(166);
        saintsRB1Stats.setRushingYards(769);
        saintsRB1Stats.setRushingTouchdowns(6);
        saintsRB1.getStats().add(saintsRB1Stats);

        //Hightower
        Stats saintsRB2Stats = new Stats();
        saintsRB2Stats.setSeason(season2015);
        saintsRB2Stats.setPlayer(saintsRB1);
        saintsRB2Stats.setPassAttempts(0);
        saintsRB2Stats.setPassCompletions(0);
        saintsRB2Stats.setPassingYards(0);
        saintsRB2Stats.setPassingTouchdowns(0);
        saintsRB2Stats.setReceptions(12);
        saintsRB2Stats.setReceivingYards(179);
        saintsRB2Stats.setReceivingTouchdowns(0);
        saintsRB2Stats.setRushingAttempts(96);
        saintsRB2Stats.setRushingYards(375);
        saintsRB2Stats.setRushingTouchdowns(4);
        saintsRB2.getStats().add(saintsRB2Stats);

        //Cooks
        Stats saintsWR1Stats = new Stats();
        saintsWR1Stats.setSeason(season2015);
        saintsWR1Stats.setPlayer(saintsRB1);
        saintsWR1Stats.setPassAttempts(0);
        saintsWR1Stats.setPassCompletions(0);
        saintsWR1Stats.setPassingYards(0);
        saintsWR1Stats.setPassingTouchdowns(0);
        saintsWR1Stats.setReceptions(84);
        saintsWR1Stats.setReceivingYards(1138);
        saintsWR1Stats.setReceivingTouchdowns(9);
        saintsWR1Stats.setRushingAttempts(8);
        saintsWR1Stats.setRushingYards(18);
        saintsWR1Stats.setRushingTouchdowns(0);
        saintsWR1.getStats().add(saintsWR1Stats);

        //Snead
        Stats saintsWR2Stats = new Stats();
        saintsWR2Stats.setSeason(season2015);
        saintsWR2Stats.setPlayer(saintsWR1);
        saintsWR2Stats.setPassAttempts(0);
        saintsWR2Stats.setPassCompletions(0);
        saintsWR2Stats.setPassingYards(0);
        saintsWR2Stats.setPassingTouchdowns(0);
        saintsWR2Stats.setReceptions(84);
        saintsWR2Stats.setReceivingYards(1138);
        saintsWR2Stats.setReceivingTouchdowns(9);
        saintsWR2Stats.setRushingAttempts(0);
        saintsWR2Stats.setRushingYards(0);
        saintsWR2Stats.setRushingTouchdowns(0);
        saintsWR2.getStats().add(saintsWR2Stats);

        //Cowboys
        //Cassel
        Stats cowboysQBStats = new Stats();
        cowboysQBStats.setSeason(season2015);
        cowboysQBStats.setPlayer(cowboysQB);
        cowboysQBStats.setPassAttempts(204);
        cowboysQBStats.setPassCompletions(119);
        cowboysQBStats.setPassingYards(1276);
        cowboysQBStats.setPassingTouchdowns(5);
        cowboysQBStats.setReceptions(0);
        cowboysQBStats.setReceivingYards(0);
        cowboysQBStats.setReceivingTouchdowns(0);
        cowboysQBStats.setRushingAttempts(15);
        cowboysQBStats.setRushingYards(78);
        cowboysQBStats.setRushingTouchdowns(0);
        cowboysQB.getStats().add(cowboysQBStats);

        //McFadden
        Stats cowboysRB1Stats = new Stats();
        cowboysRB1Stats.setSeason(season2015);
        cowboysRB1Stats.setPlayer(cowboysRB1);
        cowboysRB1Stats.setPassAttempts(0);
        cowboysRB1Stats.setPassCompletions(0);
        cowboysRB1Stats.setPassingYards(0);
        cowboysRB1Stats.setPassingTouchdowns(0);
        cowboysRB1Stats.setReceptions(40);
        cowboysRB1Stats.setReceivingYards(328);
        cowboysRB1Stats.setReceivingTouchdowns(0);
        cowboysRB1Stats.setRushingAttempts(239);
        cowboysRB1Stats.setRushingYards(1089);
        cowboysRB1Stats.setRushingTouchdowns(3);
        cowboysRB1.getStats().add(cowboysRB1Stats);

        //Randall
        Stats cowboysRB2Stats = new Stats();
        cowboysRB2Stats.setSeason(season2015);
        cowboysRB2Stats.setPlayer(cowboysRB1);
        cowboysRB2Stats.setPassAttempts(0);
        cowboysRB2Stats.setPassCompletions(0);
        cowboysRB2Stats.setPassingYards(0);
        cowboysRB2Stats.setPassingTouchdowns(0);
        cowboysRB2Stats.setReceptions(10);
        cowboysRB2Stats.setReceivingYards(86);
        cowboysRB2Stats.setReceivingTouchdowns(0);
        cowboysRB2Stats.setRushingAttempts(76);
        cowboysRB2Stats.setRushingYards(315);
        cowboysRB2Stats.setRushingTouchdowns(4);
        cowboysRB2.getStats().add(cowboysRB2Stats);

        //Williams
        Stats cowboysWR1Stats = new Stats();
        cowboysWR1Stats.setSeason(season2015);
        cowboysWR1Stats.setPlayer(cowboysRB1);
        cowboysWR1Stats.setPassAttempts(0);
        cowboysWR1Stats.setPassCompletions(0);
        cowboysWR1Stats.setPassingYards(0);
        cowboysWR1Stats.setPassingTouchdowns(0);
        cowboysWR1Stats.setReceptions(52);
        cowboysWR1Stats.setReceivingYards(840);
        cowboysWR1Stats.setReceivingTouchdowns(3);
        cowboysWR1Stats.setRushingAttempts(0);
        cowboysWR1Stats.setRushingYards(0);
        cowboysWR1Stats.setRushingTouchdowns(0);
        cowboysWR1.getStats().add(cowboysWR1Stats);

        //Beasley
        Stats cowboysWR2Stats = new Stats();
        cowboysWR2Stats.setSeason(season2015);
        cowboysWR2Stats.setPlayer(cowboysWR1);
        cowboysWR2Stats.setPassAttempts(0);
        cowboysWR2Stats.setPassCompletions(0);
        cowboysWR2Stats.setPassingYards(0);
        cowboysWR2Stats.setPassingTouchdowns(0);
        cowboysWR2Stats.setReceptions(52);
        cowboysWR2Stats.setReceivingYards(536);
        cowboysWR2Stats.setReceivingTouchdowns(5);
        cowboysWR2Stats.setRushingAttempts(0);
        cowboysWR2Stats.setRushingYards(0);
        cowboysWR2Stats.setRushingTouchdowns(0);
        cowboysWR2.getStats().add(cowboysWR2Stats);

        //Eagles
        //Bradford
        Stats eaglesQBStats = new Stats();
        eaglesQBStats.setSeason(season2015);
        eaglesQBStats.setPlayer(eaglesQB);

        eaglesQBStats.setPassAttempts(532);
        eaglesQBStats.setPassCompletions(346);
        eaglesQBStats.setPassingYards(3725);
        eaglesQBStats.setPassingTouchdowns(19);

        eaglesQBStats.setReceptions(0);
        eaglesQBStats.setReceivingYards(0);
        eaglesQBStats.setReceivingTouchdowns(0);

        eaglesQBStats.setRushingAttempts(26);
        eaglesQBStats.setRushingYards(39);
        eaglesQBStats.setRushingTouchdowns(0);

        eaglesQB.getStats().add(eaglesQBStats);

        //Murray
        Stats eaglesRB1Stats = new Stats();
        eaglesRB1Stats.setSeason(season2015);
        eaglesRB1Stats.setPlayer(eaglesRB1);

        eaglesRB1Stats.setPassAttempts(0);
        eaglesRB1Stats.setPassCompletions(0);
        eaglesRB1Stats.setPassingYards(0);
        eaglesRB1Stats.setPassingTouchdowns(0);

        eaglesRB1Stats.setReceptions(44);
        eaglesRB1Stats.setReceivingYards(322);
        eaglesRB1Stats.setReceivingTouchdowns(1);

        eaglesRB1Stats.setRushingAttempts(193);
        eaglesRB1Stats.setRushingYards(702);
        eaglesRB1Stats.setRushingTouchdowns(6);
        eaglesRB1.getStats().add(eaglesRB1Stats);

        //R. Mathews
        Stats eaglesRB2Stats = new Stats();
        eaglesRB2Stats.setSeason(season2015);
        eaglesRB2Stats.setPlayer(eaglesRB1);

        eaglesRB2Stats.setPassAttempts(0);
        eaglesRB2Stats.setPassCompletions(0);
        eaglesRB2Stats.setPassingYards(0);
        eaglesRB2Stats.setPassingTouchdowns(0);

        eaglesRB2Stats.setReceptions(20);
        eaglesRB2Stats.setReceivingYards(146);
        eaglesRB2Stats.setReceivingTouchdowns(1);

        eaglesRB2Stats.setRushingAttempts(106);
        eaglesRB2Stats.setRushingYards(539);
        eaglesRB2Stats.setRushingTouchdowns(6);

        eaglesRB2.getStats().add(eaglesRB2Stats);

        //J. Matthews
        Stats eaglesWR1Stats = new Stats();
        eaglesWR1Stats.setSeason(season2015);
        eaglesWR1Stats.setPlayer(eaglesRB1);

        eaglesWR1Stats.setPassAttempts(0);
        eaglesWR1Stats.setPassCompletions(0);
        eaglesWR1Stats.setPassingYards(0);
        eaglesWR1Stats.setPassingTouchdowns(0);

        eaglesWR1Stats.setReceptions(85);
        eaglesWR1Stats.setReceivingYards(997);
        eaglesWR1Stats.setReceivingTouchdowns(8);

        eaglesWR1Stats.setRushingAttempts(0);
        eaglesWR1Stats.setRushingYards(0);
        eaglesWR1Stats.setRushingTouchdowns(0);

        eaglesWR1.getStats().add(eaglesWR1Stats);

        //Cooper
        Stats eaglesWR2Stats = new Stats();
        eaglesWR2Stats.setSeason(season2015);
        eaglesWR2Stats.setPlayer(eaglesWR1);

        eaglesWR2Stats.setPassAttempts(0);
        eaglesWR2Stats.setPassCompletions(0);
        eaglesWR2Stats.setPassingYards(0);
        eaglesWR2Stats.setPassingTouchdowns(0);

        eaglesWR2Stats.setReceptions(21);
        eaglesWR2Stats.setReceivingYards(327);
        eaglesWR2Stats.setReceivingTouchdowns(2);

        eaglesWR2Stats.setRushingAttempts(0);
        eaglesWR2Stats.setRushingYards(0);
        eaglesWR2Stats.setRushingTouchdowns(0);

        eaglesWR2.getStats().add(eaglesWR2Stats);

        //Giants
        //Manning
        Stats giantsQBStats = new Stats();
        giantsQBStats.setSeason(season2015);
        giantsQBStats.setPlayer(giantsQB);

        giantsQBStats.setPassAttempts(618);
        giantsQBStats.setPassCompletions(387);
        giantsQBStats.setPassingYards(4436);
        giantsQBStats.setPassingTouchdowns(35);

        giantsQBStats.setReceptions(0);
        giantsQBStats.setReceivingYards(0);
        giantsQBStats.setReceivingTouchdowns(0);

        giantsQBStats.setRushingAttempts(20);
        giantsQBStats.setRushingYards(61);
        giantsQBStats.setRushingTouchdowns(0);

        giantsQB.getStats().add(giantsQBStats);

        //Jennings
        Stats giantsRB1Stats = new Stats();
        giantsRB1Stats.setSeason(season2015);
        giantsRB1Stats.setPlayer(giantsRB1);

        giantsRB1Stats.setPassAttempts(0);
        giantsRB1Stats.setPassCompletions(0);
        giantsRB1Stats.setPassingYards(0);
        giantsRB1Stats.setPassingTouchdowns(0);

        giantsRB1Stats.setReceptions(29);
        giantsRB1Stats.setReceivingYards(296);
        giantsRB1Stats.setReceivingTouchdowns(1);

        giantsRB1Stats.setRushingAttempts(193);
        giantsRB1Stats.setRushingYards(702);
        giantsRB1Stats.setRushingTouchdowns(6);
        giantsRB1.getStats().add(giantsRB1Stats);

        //Vareen
        Stats giantsRB2Stats = new Stats();
        giantsRB2Stats.setSeason(season2015);
        giantsRB2Stats.setPlayer(giantsRB1);

        giantsRB2Stats.setPassAttempts(0);
        giantsRB2Stats.setPassCompletions(0);
        giantsRB2Stats.setPassingYards(0);
        giantsRB2Stats.setPassingTouchdowns(0);

        giantsRB2Stats.setReceptions(59);
        giantsRB2Stats.setReceivingYards(495);
        giantsRB2Stats.setReceivingTouchdowns(4);

        giantsRB2Stats.setRushingAttempts(61);
        giantsRB2Stats.setRushingYards(260);
        giantsRB2Stats.setRushingTouchdowns(0);

        giantsRB2.getStats().add(giantsRB2Stats);

        //Beckham Jr.
        Stats giantsWR1Stats = new Stats();
        giantsWR1Stats.setSeason(season2015);
        giantsWR1Stats.setPlayer(giantsRB1);

        giantsWR1Stats.setPassAttempts(0);
        giantsWR1Stats.setPassCompletions(0);
        giantsWR1Stats.setPassingYards(0);
        giantsWR1Stats.setPassingTouchdowns(0);

        giantsWR1Stats.setReceptions(96);
        giantsWR1Stats.setReceivingYards(1450);
        giantsWR1Stats.setReceivingTouchdowns(13);

        giantsWR1Stats.setRushingAttempts(1);
        giantsWR1Stats.setRushingYards(3);
        giantsWR1Stats.setRushingTouchdowns(0);

        giantsWR1.getStats().add(giantsWR1Stats);

        //Randle
        Stats giantsWR2Stats = new Stats();
        giantsWR2Stats.setSeason(season2015);
        giantsWR2Stats.setPlayer(giantsWR1);

        giantsWR2Stats.setPassAttempts(0);
        giantsWR2Stats.setPassCompletions(0);
        giantsWR2Stats.setPassingYards(0);
        giantsWR2Stats.setPassingTouchdowns(0);

        giantsWR2Stats.setReceptions(57);
        giantsWR2Stats.setReceivingYards(797);
        giantsWR2Stats.setReceivingTouchdowns(8);

        giantsWR2Stats.setRushingAttempts(0);
        giantsWR2Stats.setRushingYards(0);
        giantsWR2Stats.setRushingTouchdowns(0);

        giantsWR2.getStats().add(giantsWR2Stats);

        //Redskins
        //Cousins
        Stats redskinsQBStats = new Stats();
        redskinsQBStats.setSeason(season2015);
        redskinsQBStats.setPlayer(redskinsQB);

        redskinsQBStats.setPassAttempts(543);
        redskinsQBStats.setPassCompletions(379);
        redskinsQBStats.setPassingYards(4166);
        redskinsQBStats.setPassingTouchdowns(29);

        redskinsQBStats.setReceptions(0);
        redskinsQBStats.setReceivingYards(0);
        redskinsQBStats.setReceivingTouchdowns(0);

        redskinsQBStats.setRushingAttempts(26);
        redskinsQBStats.setRushingYards(48);
        redskinsQBStats.setRushingTouchdowns(5);

        redskinsQB.getStats().add(redskinsQBStats);

        //Morris
        Stats redskinsRB1Stats = new Stats();
        redskinsRB1Stats.setSeason(season2015);
        redskinsRB1Stats.setPlayer(redskinsRB1);

        redskinsRB1Stats.setPassAttempts(0);
        redskinsRB1Stats.setPassCompletions(0);
        redskinsRB1Stats.setPassingYards(0);
        redskinsRB1Stats.setPassingTouchdowns(0);

        redskinsRB1Stats.setReceptions(10);
        redskinsRB1Stats.setReceivingYards(55);
        redskinsRB1Stats.setReceivingTouchdowns(0);

        redskinsRB1Stats.setRushingAttempts(202);
        redskinsRB1Stats.setRushingYards(751);
        redskinsRB1Stats.setRushingTouchdowns(1);
        redskinsRB1.getStats().add(redskinsRB1Stats);

        //Jones
        Stats redskinsRB2Stats = new Stats();
        redskinsRB2Stats.setSeason(season2015);
        redskinsRB2Stats.setPlayer(redskinsRB1);

        redskinsRB2Stats.setPassAttempts(0);
        redskinsRB2Stats.setPassCompletions(0);
        redskinsRB2Stats.setPassingYards(0);
        redskinsRB2Stats.setPassingTouchdowns(0);

        redskinsRB2Stats.setReceptions(19);
        redskinsRB2Stats.setReceivingYards(304);
        redskinsRB2Stats.setReceivingTouchdowns(1);

        redskinsRB2Stats.setRushingAttempts(59);
        redskinsRB2Stats.setRushingYards(490);
        redskinsRB2Stats.setRushingTouchdowns(3);

        redskinsRB2.getStats().add(redskinsRB2Stats);

        //Garson
        Stats redskinsWR1Stats = new Stats();
        redskinsWR1Stats.setSeason(season2015);
        redskinsWR1Stats.setPlayer(redskinsRB1);

        redskinsWR1Stats.setPassAttempts(0);
        redskinsWR1Stats.setPassCompletions(0);
        redskinsWR1Stats.setPassingYards(0);
        redskinsWR1Stats.setPassingTouchdowns(0);

        redskinsWR1Stats.setReceptions(72);
        redskinsWR1Stats.setReceivingYards(777);
        redskinsWR1Stats.setReceivingTouchdowns(6);

        redskinsWR1Stats.setRushingAttempts(0);
        redskinsWR1Stats.setRushingYards(0);
        redskinsWR1Stats.setRushingTouchdowns(0);

        redskinsWR1.getStats().add(redskinsWR1Stats);

        //Crowder
        Stats redskinsWR2Stats = new Stats();
        redskinsWR2Stats.setSeason(season2015);
        redskinsWR2Stats.setPlayer(redskinsWR1);

        redskinsWR2Stats.setPassAttempts(1);
        redskinsWR2Stats.setPassCompletions(0);
        redskinsWR2Stats.setPassingYards(0);
        redskinsWR2Stats.setPassingTouchdowns(0);

        redskinsWR2Stats.setReceptions(59);
        redskinsWR2Stats.setReceivingYards(604);
        redskinsWR2Stats.setReceivingTouchdowns(2);

        redskinsWR2Stats.setRushingAttempts(2);
        redskinsWR2Stats.setRushingYards(2);
        redskinsWR2Stats.setRushingTouchdowns(0);

        redskinsWR2.getStats().add(redskinsWR2Stats);

        //Bears
        //Cutler
        Stats bearsQBStats = new Stats();
        bearsQBStats.setSeason(season2015);
        bearsQBStats.setPlayer(bearsQB);

        bearsQBStats.setPassAttempts(483);
        bearsQBStats.setPassCompletions(311);
        bearsQBStats.setPassingYards(3659);
        bearsQBStats.setPassingTouchdowns(21);

        bearsQBStats.setReceptions(0);
        bearsQBStats.setReceivingYards(0);
        bearsQBStats.setReceivingTouchdowns(0);

        bearsQBStats.setRushingAttempts(38);
        bearsQBStats.setRushingYards(201);
        bearsQBStats.setRushingTouchdowns(1);

        bearsQB.getStats().add(bearsQBStats);

        //Forte
        Stats bearsRB1Stats = new Stats();
        bearsRB1Stats.setSeason(season2015);
        bearsRB1Stats.setPlayer(bearsRB1);

        bearsRB1Stats.setPassAttempts(0);
        bearsRB1Stats.setPassCompletions(0);
        bearsRB1Stats.setPassingYards(0);
        bearsRB1Stats.setPassingTouchdowns(0);

        bearsRB1Stats.setReceptions(44);
        bearsRB1Stats.setReceivingYards(389);
        bearsRB1Stats.setReceivingTouchdowns(3);

        bearsRB1Stats.setRushingAttempts(218);
        bearsRB1Stats.setRushingYards(898);
        bearsRB1Stats.setRushingTouchdowns(4);
        bearsRB1.getStats().add(bearsRB1Stats);

        //Langford
        Stats bearsRB2Stats = new Stats();
        bearsRB2Stats.setSeason(season2015);
        bearsRB2Stats.setPlayer(bearsRB1);

        bearsRB2Stats.setPassAttempts(0);
        bearsRB2Stats.setPassCompletions(0);
        bearsRB2Stats.setPassingYards(0);
        bearsRB2Stats.setPassingTouchdowns(0);

        bearsRB2Stats.setReceptions(19);
        bearsRB2Stats.setReceivingYards(304);
        bearsRB2Stats.setReceivingTouchdowns(1);

        bearsRB2Stats.setRushingAttempts(22);
        bearsRB2Stats.setRushingYards(279);
        bearsRB2Stats.setRushingTouchdowns(1);

        bearsRB2.getStats().add(bearsRB2Stats);

        //Jeffery
        Stats bearsWR1Stats = new Stats();
        bearsWR1Stats.setSeason(season2015);
        bearsWR1Stats.setPlayer(bearsRB1);

        bearsWR1Stats.setPassAttempts(0);
        bearsWR1Stats.setPassCompletions(0);
        bearsWR1Stats.setPassingYards(0);
        bearsWR1Stats.setPassingTouchdowns(0);

        bearsWR1Stats.setReceptions(54);
        bearsWR1Stats.setReceivingYards(807);
        bearsWR1Stats.setReceivingTouchdowns(4);

        bearsWR1Stats.setRushingAttempts(0);
        bearsWR1Stats.setRushingYards(0);
        bearsWR1Stats.setRushingTouchdowns(0);

        bearsWR1.getStats().add(bearsWR1Stats);

        //Wilson
        Stats bearsWR2Stats = new Stats();
        bearsWR2Stats.setSeason(season2015);
        bearsWR2Stats.setPlayer(bearsWR1);

        bearsWR2Stats.setPassAttempts(0);
        bearsWR2Stats.setPassCompletions(0);
        bearsWR2Stats.setPassingYards(0);
        bearsWR2Stats.setPassingTouchdowns(0);

        bearsWR2Stats.setReceptions(28);
        bearsWR2Stats.setReceivingYards(464);
        bearsWR2Stats.setReceivingTouchdowns(1);

        bearsWR2Stats.setRushingAttempts(0);
        bearsWR2Stats.setRushingYards(0);
        bearsWR2Stats.setRushingTouchdowns(0);

        bearsWR2.getStats().add(bearsWR2Stats);

        //Lions
        //Stafford
        Stats lionsQBStats = new Stats();
        lionsQBStats.setSeason(season2015);
        lionsQBStats.setPlayer(lionsQB);

        lionsQBStats.setPassAttempts(592);
        lionsQBStats.setPassCompletions(398);
        lionsQBStats.setPassingYards(4262);
        lionsQBStats.setPassingTouchdowns(32);

        lionsQBStats.setReceptions(0);
        lionsQBStats.setReceivingYards(0);
        lionsQBStats.setReceivingTouchdowns(0);

        lionsQBStats.setRushingAttempts(44);
        lionsQBStats.setRushingYards(201);
        lionsQBStats.setRushingTouchdowns(1);

        lionsQB.getStats().add(lionsQBStats);

        //Abdullah
        Stats lionsRB1Stats = new Stats();
        lionsRB1Stats.setSeason(season2015);
        lionsRB1Stats.setPlayer(lionsRB1);

        lionsRB1Stats.setPassAttempts(0);
        lionsRB1Stats.setPassCompletions(0);
        lionsRB1Stats.setPassingYards(0);
        lionsRB1Stats.setPassingTouchdowns(0);

        lionsRB1Stats.setReceptions(25);
        lionsRB1Stats.setReceivingYards(183);
        lionsRB1Stats.setReceivingTouchdowns(1);

        lionsRB1Stats.setRushingAttempts(143);
        lionsRB1Stats.setRushingYards(597);
        lionsRB1Stats.setRushingTouchdowns(2);
        lionsRB1.getStats().add(lionsRB1Stats);

        //Bell
        Stats lionsRB2Stats = new Stats();
        lionsRB2Stats.setSeason(season2015);
        lionsRB2Stats.setPlayer(lionsRB1);

        lionsRB2Stats.setPassAttempts(0);
        lionsRB2Stats.setPassCompletions(0);
        lionsRB2Stats.setPassingYards(0);
        lionsRB2Stats.setPassingTouchdowns(0);

        lionsRB2Stats.setReceptions(22);
        lionsRB2Stats.setReceivingYards(286);
        lionsRB2Stats.setReceivingTouchdowns(0);

        lionsRB2Stats.setRushingAttempts(90);
        lionsRB2Stats.setRushingYards(311);
        lionsRB2Stats.setRushingTouchdowns(4);

        lionsRB2.getStats().add(lionsRB2Stats);

        //Johnson
        Stats lionsWR1Stats = new Stats();
        lionsWR1Stats.setSeason(season2015);
        lionsWR1Stats.setPlayer(lionsRB1);

        lionsWR1Stats.setPassAttempts(0);
        lionsWR1Stats.setPassCompletions(0);
        lionsWR1Stats.setPassingYards(0);
        lionsWR1Stats.setPassingTouchdowns(0);

        lionsWR1Stats.setReceptions(1214);
        lionsWR1Stats.setReceivingYards(1214);
        lionsWR1Stats.setReceivingTouchdowns(9);

        lionsWR1Stats.setRushingAttempts(0);
        lionsWR1Stats.setRushingYards(0);
        lionsWR1Stats.setRushingTouchdowns(0);

        lionsWR1.getStats().add(lionsWR1Stats);

        //Tate
        Stats lionsWR2Stats = new Stats();
        lionsWR2Stats.setSeason(season2015);
        lionsWR2Stats.setPlayer(lionsWR1);

        lionsWR2Stats.setPassAttempts(0);
        lionsWR2Stats.setPassCompletions(0);
        lionsWR2Stats.setPassingYards(0);
        lionsWR2Stats.setPassingTouchdowns(0);

        lionsWR2Stats.setReceptions(90);
        lionsWR2Stats.setReceivingYards(813);
        lionsWR2Stats.setReceivingTouchdowns(6);

        lionsWR2Stats.setRushingAttempts(6);
        lionsWR2Stats.setRushingYards(41);
        lionsWR2Stats.setRushingTouchdowns(0);

        lionsWR2.getStats().add(lionsWR2Stats);

        //Packers
        //Rodgers
        Stats packersQBStats = new Stats();
        packersQBStats.setSeason(season2015);
        packersQBStats.setPlayer(packersQB);

        packersQBStats.setPassAttempts(572);
        packersQBStats.setPassCompletions(347);
        packersQBStats.setPassingYards(3821);
        packersQBStats.setPassingTouchdowns(31);

        packersQBStats.setReceptions(0);
        packersQBStats.setReceivingYards(0);
        packersQBStats.setReceivingTouchdowns(0);

        packersQBStats.setRushingAttempts(58);
        packersQBStats.setRushingYards(344);
        packersQBStats.setRushingTouchdowns(1);

        packersQB.getStats().add(packersQBStats);

        //Lacy
        Stats packersRB1Stats = new Stats();
        packersRB1Stats.setSeason(season2015);
        packersRB1Stats.setPlayer(packersRB1);

        packersRB1Stats.setPassAttempts(0);
        packersRB1Stats.setPassCompletions(0);
        packersRB1Stats.setPassingYards(0);
        packersRB1Stats.setPassingTouchdowns(0);

        packersRB1Stats.setReceptions(20);
        packersRB1Stats.setReceivingYards(188);
        packersRB1Stats.setReceivingTouchdowns(2);

        packersRB1Stats.setRushingAttempts(187);
        packersRB1Stats.setRushingYards(758);
        packersRB1Stats.setRushingTouchdowns(3);
        packersRB1.getStats().add(packersRB1Stats);

        //Starks
        Stats packersRB2Stats = new Stats();
        packersRB2Stats.setSeason(season2015);
        packersRB2Stats.setPlayer(packersRB1);

        packersRB2Stats.setPassAttempts(0);
        packersRB2Stats.setPassCompletions(0);
        packersRB2Stats.setPassingYards(0);
        packersRB2Stats.setPassingTouchdowns(0);

        packersRB2Stats.setReceptions(43);
        packersRB2Stats.setReceivingYards(392);
        packersRB2Stats.setReceivingTouchdowns(3);

        packersRB2Stats.setRushingAttempts(148);
        packersRB2Stats.setRushingYards(601);
        packersRB2Stats.setRushingTouchdowns(2);

        packersRB2.getStats().add(packersRB2Stats);

        //Jones
        Stats packersWR1Stats = new Stats();
        packersWR1Stats.setSeason(season2015);
        packersWR1Stats.setPlayer(packersRB1);

        packersWR1Stats.setPassAttempts(0);
        packersWR1Stats.setPassCompletions(0);
        packersWR1Stats.setPassingYards(0);
        packersWR1Stats.setPassingTouchdowns(0);

        packersWR1Stats.setReceptions(50);
        packersWR1Stats.setReceivingYards(890);
        packersWR1Stats.setReceivingTouchdowns(9);

        packersWR1Stats.setRushingAttempts(0);
        packersWR1Stats.setRushingYards(0);
        packersWR1Stats.setRushingTouchdowns(0);

        packersWR1.getStats().add(packersWR1Stats);

        //Cobb
        Stats packersWR2Stats = new Stats();
        packersWR2Stats.setSeason(season2015);
        packersWR2Stats.setPlayer(packersWR1);

        packersWR2Stats.setPassAttempts(0);
        packersWR2Stats.setPassCompletions(0);
        packersWR2Stats.setPassingYards(0);
        packersWR2Stats.setPassingTouchdowns(0);

        packersWR2Stats.setReceptions(79);
        packersWR2Stats.setReceivingYards(829);
        packersWR2Stats.setReceivingTouchdowns(6);

        packersWR2Stats.setRushingAttempts(13);
        packersWR2Stats.setRushingYards(50);
        packersWR2Stats.setRushingTouchdowns(0);

        packersWR2.getStats().add(packersWR2Stats);

        //Vikings
        //Bridgewater
        Stats vikingsQBStats = new Stats();
        vikingsQBStats.setSeason(season2015);
        vikingsQBStats.setPlayer(vikingsQB);

        vikingsQBStats.setPassAttempts(447);
        vikingsQBStats.setPassCompletions(292);
        vikingsQBStats.setPassingYards(3231);
        vikingsQBStats.setPassingTouchdowns(14);

        vikingsQBStats.setReceptions(0);
        vikingsQBStats.setReceivingYards(0);
        vikingsQBStats.setReceivingTouchdowns(0);

        vikingsQBStats.setRushingAttempts(44);
        vikingsQBStats.setRushingYards(192);
        vikingsQBStats.setRushingTouchdowns(3);

        vikingsQB.getStats().add(vikingsQBStats);

        //Peterson
        Stats vikingsRB1Stats = new Stats();
        vikingsRB1Stats.setSeason(season2015);
        vikingsRB1Stats.setPlayer(vikingsRB1);

        vikingsRB1Stats.setPassAttempts(0);
        vikingsRB1Stats.setPassCompletions(0);
        vikingsRB1Stats.setPassingYards(0);
        vikingsRB1Stats.setPassingTouchdowns(0);

        vikingsRB1Stats.setReceptions(30);
        vikingsRB1Stats.setReceivingYards(222);
        vikingsRB1Stats.setReceivingTouchdowns(0);

        vikingsRB1Stats.setRushingAttempts(327);
        vikingsRB1Stats.setRushingYards(1485);
        vikingsRB1Stats.setRushingTouchdowns(11);
        vikingsRB1.getStats().add(vikingsRB1Stats);

        //McKinnon
        Stats vikingsRB2Stats = new Stats();
        vikingsRB2Stats.setSeason(season2015);
        vikingsRB2Stats.setPlayer(vikingsRB1);

        vikingsRB2Stats.setPassAttempts(0);
        vikingsRB2Stats.setPassCompletions(0);
        vikingsRB2Stats.setPassingYards(0);
        vikingsRB2Stats.setPassingTouchdowns(0);

        vikingsRB2Stats.setReceptions(43);
        vikingsRB2Stats.setReceivingYards(392);
        vikingsRB2Stats.setReceivingTouchdowns(3);

        vikingsRB2Stats.setRushingAttempts(52);
        vikingsRB2Stats.setRushingYards(271);
        vikingsRB2Stats.setRushingTouchdowns(2);

        vikingsRB2.getStats().add(vikingsRB2Stats);

        //Diggs
        Stats vikingsWR1Stats = new Stats();
        vikingsWR1Stats.setSeason(season2015);
        vikingsWR1Stats.setPlayer(vikingsWR1);

        vikingsWR1Stats.setPassAttempts(0);
        vikingsWR1Stats.setPassCompletions(0);
        vikingsWR1Stats.setPassingYards(0);
        vikingsWR1Stats.setPassingTouchdowns(0);

        vikingsWR1Stats.setReceptions(52);
        vikingsWR1Stats.setReceivingYards(720);
        vikingsWR1Stats.setReceivingTouchdowns(4);

        vikingsWR1Stats.setRushingAttempts(3);
        vikingsWR1Stats.setRushingYards(13);
        vikingsWR1Stats.setRushingTouchdowns(0);

        vikingsWR1.getStats().add(vikingsWR1Stats);

        //Wallace
        Stats vikingsWR2Stats = new Stats();
        vikingsWR2Stats.setSeason(season2015);
        vikingsWR2Stats.setPlayer(vikingsRB1);

        vikingsWR2Stats.setPassAttempts(0);
        vikingsWR2Stats.setPassCompletions(0);
        vikingsWR2Stats.setPassingYards(0);
        vikingsWR2Stats.setPassingTouchdowns(0);

        vikingsWR2Stats.setReceptions(39);
        vikingsWR2Stats.setReceivingYards(473);
        vikingsWR2Stats.setReceivingTouchdowns(2);

        vikingsWR2Stats.setRushingAttempts(1);
        vikingsWR2Stats.setRushingYards(6);
        vikingsWR2Stats.setRushingTouchdowns(0);

        vikingsWR1.getStats().add(vikingsWR1Stats);

        //save everything
        manager.saveEntity(nfl);

    }

}
