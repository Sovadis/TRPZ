package ua.kpi.trpz.nantbuilder.repository.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.kpi.trpz.nantbuilder.db.DataAccessException;
import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.TaskType;
import ua.kpi.trpz.nantbuilder.repository.BuildScriptRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcBuildScriptRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcPropertyRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcTargetRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcTaskRepository;
import ua.kpi.trpz.nantbuilder.service.ScriptService;
import ua.kpi.trpz.nantbuilder.service.ValidationException;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachingBuildScriptRepositoryTest {

    private final CountingRepository realRepository = new CountingRepository();
    private final CacheStatistics statistics = new CacheStatistics();
    private final CachingBuildScriptRepository proxy = new CachingBuildScriptRepository(realRepository, statistics);

    @BeforeEach
    void fillRealRepository() {
        realRepository.insert(header("Alpha"));
        realRepository.insert(header("Beta"));
    }

    @Test
    void repeatedFindAllQueriesRealRepositoryOnce() {
        List<BuildScript> first = proxy.findAll();
        List<BuildScript> second = proxy.findAll();
        List<BuildScript> third = proxy.findAll();

        assertEquals(1, realRepository.findAllCalls);
        assertEquals(List.of("Alpha", "Beta"), names(first));
        assertEquals(names(first), names(second));
        assertEquals(names(first), names(third));
        assertCounters(2, 1, 0);
    }

    @Test
    void findByIdAndFindByNameAfterFindAllAreServedFromCache() {
        proxy.findAll();

        assertEquals("Beta", proxy.findById(2L).orElseThrow().getName());
        assertEquals(1L, proxy.findByName("Alpha").orElseThrow().getId());
        // відсутній у завантаженому списку запис не шукається в БД
        assertTrue(proxy.findById(99L).isEmpty());
        assertTrue(proxy.findByName("Gamma").isEmpty());
        // пошук за назвою чутливий до регістру, як WHERE name = ? у SQLite
        assertTrue(proxy.findByName("alpha").isEmpty());

        assertEquals(0, realRepository.findByIdCalls);
        assertEquals(0, realRepository.findByNameCalls);
        assertCounters(5, 1, 0);
    }

    @Test
    void findByIdAndFindByNameWithEmptyCacheAreDelegatedWithoutLoadingList() {
        assertEquals("Alpha", proxy.findById(1L).orElseThrow().getName());
        assertEquals(2L, proxy.findByName("Beta").orElseThrow().getId());
        assertTrue(proxy.findByName("Gamma").isEmpty());

        assertEquals(1, realRepository.findByIdCalls);
        assertEquals(2, realRepository.findByNameCalls);
        assertEquals(0, realRepository.findAllCalls);
        assertCounters(0, 3, 0);
    }

    @Test
    void saveIsDelegatedAndInvalidatesCache() {
        proxy.findAll();
        BuildScript gamma = header("Gamma");

        BuildScript saved = proxy.save(gamma);

        assertSame(gamma, saved, "save повертає переданий об'єкт зі встановленим id, як і реальний репозиторій");
        assertEquals(3L, gamma.getId());
        assertEquals(1, realRepository.saveCalls);
        assertCounters(0, 1, 1);

        assertEquals(List.of("Alpha", "Beta", "Gamma"), names(proxy.findAll()));
        assertEquals(2, realRepository.findAllCalls);
        assertCounters(0, 2, 1);
    }

    @Test
    void deleteByIdIsDelegatedAndInvalidatesCache() {
        proxy.findAll();

        proxy.deleteById(1L);

        assertEquals(1, realRepository.deleteCalls);
        assertEquals(List.of("Beta"), names(proxy.findAll()));
        assertEquals(2, realRepository.findAllCalls);
        assertTrue(proxy.findById(1L).isEmpty());
        assertCounters(1, 2, 1);
    }

    @Test
    void failedSaveStillInvalidatesCache() {
        proxy.findAll();
        realRepository.failOnSave = new DataAccessException("Імітований збій");

        assertThrows(DataAccessException.class, () -> proxy.save(header("Gamma")));

        assertCounters(0, 1, 1);
        proxy.findAll();
        assertEquals(2, realRepository.findAllCalls);
    }

    @Test
    void invalidateForcesNextReadFromRealRepository() {
        proxy.findAll();
        realRepository.insert(header("Changed outside"));

        assertEquals(2, proxy.findAll().size(), "до скидання кеш повертає збережений список");
        proxy.invalidate();
        assertEquals(3, proxy.findAll().size());

        assertEquals(2, realRepository.findAllCalls);
        assertCounters(1, 2, 1);
    }

    @Test
    void returnedObjectsAreCopiesOfHeaders() {
        BuildScript stored = realRepository.rows.get(1L);
        stored.setDescription("Опис");
        stored.setDefaultTarget("build");
        stored.setBaseDir("src");

        List<BuildScript> list = proxy.findAll();
        BuildScript fromList = list.get(0);
        BuildScript byId = proxy.findById(1L).orElseThrow();
        BuildScript byName = proxy.findByName("Alpha").orElseThrow();

        // копії містять усі поля заголовка, але є окремими об'єктами
        for (BuildScript copy : List.of(fromList, byId, byName)) {
            assertNotSame(stored, copy);
            assertEquals(stored.getId(), copy.getId());
            assertEquals(stored.getName(), copy.getName());
            assertEquals(stored.getDescription(), copy.getDescription());
            assertEquals(stored.getDefaultTarget(), copy.getDefaultTarget());
            assertEquals(stored.getBaseDir(), copy.getBaseDir());
            assertEquals(stored.getCreatedAt(), copy.getCreatedAt());
            assertEquals(stored.getUpdatedAt(), copy.getUpdatedAt());
        }
        assertNotSame(fromList, byId);
        assertNotSame(byId, byName);

        // зміни отриманих об'єктів і списку не потрапляють у кеш
        fromList.setName("Corrupted");
        byId.setDescription("Corrupted");
        list.clear();
        BuildScript again = proxy.findById(1L).orElseThrow();
        assertEquals("Alpha", again.getName());
        assertEquals("Опис", again.getDescription());
        assertEquals(List.of("Alpha", "Beta"), names(proxy.findAll()));
        assertEquals(1, realRepository.findAllCalls);
    }

    @Test
    void copiesFromRealRepositoryAreAlsoReturnedWhenCacheIsEmpty() {
        BuildScript stored = realRepository.rows.get(2L);

        BuildScript byId = proxy.findById(2L).orElseThrow();
        BuildScript byName = proxy.findByName("Beta").orElseThrow();

        assertNotSame(stored, byId);
        assertNotSame(stored, byName);
        byId.setName("Corrupted");
        assertEquals("Beta", stored.getName());
    }

    @Test
    void statisticsReflectTypicalSession() {
        proxy.findAll();                 // miss: перше відкриття головної форми
        proxy.findAll();                 // hit: активація вікна
        proxy.findByName("Gamma");       // hit: перевірка унікальності назви
        proxy.save(header("Gamma"));     // invalidation
        proxy.findAll();                 // miss: оновлення списку після створення
        proxy.findById(3L);              // hit: відкриття редактора
        proxy.findAll();                 // hit: повернення з редактора
        proxy.deleteById(3L);            // invalidation
        proxy.findById(3L);              // miss: кеш порожній, запит до БД

        assertCounters(4, 3, 2);
        assertEquals(2, realRepository.findAllCalls);
        assertEquals(1, realRepository.findByIdCalls);
        assertEquals(0, realRepository.findByNameCalls);
    }

    private void assertCounters(long hits, long misses, long invalidations) {
        assertEquals(hits, statistics.getHits(), "hits");
        assertEquals(misses, statistics.getMisses(), "misses");
        assertEquals(invalidations, statistics.getInvalidations(), "invalidations");
    }

    private static BuildScript header(String name) {
        BuildScript script = new BuildScript();
        script.setName(name);
        script.setCreatedAt(LocalDateTime.of(2026, 11, 2, 14, 5));
        script.setUpdatedAt(LocalDateTime.of(2026, 11, 3, 9, 30));
        return script;
    }

    private static List<String> names(List<BuildScript> scripts) {
        return scripts.stream().map(BuildScript::getName).toList();
    }

    /**
     * Заглушка реального репозиторію: тримає заголовки в пам'яті, рахує звернення і, на відміну від БД,
     * повертає ті самі об'єкти — так видно, що замісник віддає копії.
     */
    private static final class CountingRepository implements BuildScriptRepository {
        private final Map<Long, BuildScript> rows = new LinkedHashMap<>();
        private long nextId = 1;
        private int findAllCalls;
        private int findByIdCalls;
        private int findByNameCalls;
        private int saveCalls;
        private int deleteCalls;
        private RuntimeException failOnSave;

        /** Додає запис напряму, без підрахунку звернень. */
        void insert(BuildScript script) {
            script.setId(nextId++);
            rows.put(script.getId(), script);
        }

        @Override
        public Optional<BuildScript> findById(Long id) {
            findByIdCalls++;
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<BuildScript> findByName(String name) {
            findByNameCalls++;
            return rows.values().stream().filter(script -> script.getName().equals(name)).findFirst();
        }

        @Override
        public List<BuildScript> findAll() {
            findAllCalls++;
            return new ArrayList<>(rows.values());
        }

        @Override
        public BuildScript save(BuildScript entity) {
            saveCalls++;
            if (failOnSave != null) {
                throw failOnSave;
            }
            if (entity.getId() == null) {
                entity.setId(nextId++);
            }
            rows.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public void deleteById(Long id) {
            deleteCalls++;
            rows.remove(id);
        }
    }

    /** Інтеграційні тести: замісник поверх JdbcBuildScriptRepository і ScriptService на тимчасовій БД SQLite. */
    @Nested
    class WithJdbcRepository {

        @TempDir
        Path tempDir;

        private Database db;
        private CacheStatistics jdbcStatistics;
        private ScriptService service;

        @BeforeEach
        void setUp() {
            db = Database.openFile(tempDir.resolve("test.db"));
            db.initSchema();
            jdbcStatistics = new CacheStatistics();
            service = newService(new CachingBuildScriptRepository(new JdbcBuildScriptRepository(db), jdbcStatistics),
                    new JdbcTaskRepository(db));
        }

        @AfterEach
        void tearDown() {
            db.close();
        }

        @Test
        void createdScriptAppearsInCachedList() {
            assertTrue(service.listScripts().isEmpty());
            assertTrue(service.listScripts().isEmpty());
            assertEquals(1, jdbcStatistics.getHits());

            service.createScript("MyLibrary", "Бібліотека", ".");

            assertEquals(List.of("MyLibrary"), names(service.listScripts()));
            assertEquals(1, jdbcStatistics.getInvalidations());
            assertEquals(2, jdbcStatistics.getMisses());
        }

        @Test
        void duplicateNameIsDetectedFromCache() {
            service.createScript("MyLibrary", null, ".");
            service.listScripts();
            long missesBefore = jdbcStatistics.getMisses();

            ValidationException error = assertThrows(ValidationException.class,
                    () -> service.createScript("MyLibrary", null, "."));

            assertEquals("Скрипт з назвою «MyLibrary» уже існує", error.getMessage());
            assertEquals(missesBefore, jdbcStatistics.getMisses());
        }

        @Test
        void renamedAndDeletedScriptsAreVisibleInList() {
            BuildScript first = service.createScript("Beta", null, ".");
            service.createScript("Gamma", null, ".");
            assertEquals(List.of("Beta", "Gamma"), names(service.listScripts()));

            BuildScript loaded = service.loadScript(first.getId());
            loaded.setName("Alpha");
            // зміна завантаженого (ще не збереженого) скрипта не впливає на список
            assertEquals(List.of("Beta", "Gamma"), names(service.listScripts()));

            service.saveScript(loaded);
            assertEquals(List.of("Alpha", "Gamma"), names(service.listScripts()));

            service.deleteScript(first.getId());
            assertEquals(List.of("Gamma"), names(service.listScripts()));
        }

        @Test
        void rolledBackSaveDoesNotLeaveStaleCache() {
            CachingBuildScriptRepository scripts =
                    new CachingBuildScriptRepository(new JdbcBuildScriptRepository(db), jdbcStatistics);
            ScriptService workingService = newService(scripts, new JdbcTaskRepository(db));
            ScriptService failingService = newService(scripts, new JdbcTaskRepository(db) {
                @Override
                public BuildTask save(BuildTask task) {
                    throw new DataAccessException("Імітований збій запису завдання");
                }
            });

            BuildScript script = workingService.createScript("Old", null, ".");
            workingService.listScripts();
            BuildScript edited = workingService.loadScript(script.getId());
            edited.setName("New");
            edited.findTarget("build").orElseThrow().addTask(BuildTask.withDefaults(TaskType.ECHO));
            long missesBefore = jdbcStatistics.getMisses();

            // заголовок уже оновлено (UPDATE build_script), але запис завдання падає -> rollback
            assertThrows(DataAccessException.class, () -> failingService.saveScript(edited));

            assertEquals(List.of("Old"), names(workingService.listScripts()));
            assertEquals(missesBefore + 1, jdbcStatistics.getMisses(), "після відкату список перечитано з БД");
        }

        private ScriptService newService(BuildScriptRepository scripts, JdbcTaskRepository tasks) {
            return new ScriptService(db, scripts, new JdbcPropertyRepository(db), new JdbcTargetRepository(db), tasks);
        }
    }
}
